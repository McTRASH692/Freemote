package com.mctrash692.freemote.remote.androidtv;

// ============================================================================
// FILE: AndroidTvRemote.java
// WHAT:  Controls Android TV / Google TV devices over the network.
//        Connects using a TLS-encrypted socket and communicates via Google's
//        protobuf format.  Can send remote keys, mouse movements, text input,
//        and launch apps.
// ============================================================================

import android.util.Log;

import com.mctrash692.freemote.remote.BaseRemote;
import com.mctrash692.freemote.remote.androidtv.proto.RemoteProto;
import com.mctrash692.freemote.util.SslUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLSocket;

public class AndroidTvRemote extends BaseRemote {

    // ==========================================================================
    // SECTION: CLASS DATA
    // WHAT:  Stores the TLS socket, data streams, background thread pool, and
    //        state flags needed to communicate with an Android TV device.
    // ==========================================================================

    /** The standard port that Android TV remote services listen on. */
    private static final int PORT = 6466;

    /** Runs tasks one at a time so commands are sent in order. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    /** Set to true when the user requests disconnect; stops the read loop. */
    private volatile boolean intentionalStop = false;
    /** The thread that continuously reads incoming protobuf messages. */
    private volatile Thread readThread;

    /** The encrypted socket connection to the Android TV. */
    private volatile SSLSocket socket;
    /** Reads binary data coming from the TV. */
    private volatile DataInputStream in;
    /** Writes binary data going to the TV. */
    private volatile DataOutputStream out;

    // ==========================================================================
    // SECTION: CONSTRUCTOR
    // WHAT:  Stores the TV's IP address and the listener object.  The actual
    //        connection does not start until connect() is called.
    // ==========================================================================

    public AndroidTvRemote(String ip, Listener listener) {
        super("AndroidTvRemote", ip, listener);
    }

    // =========================================================================
    // SECTION: CONNECTION
    // WHAT:  Opens the TLS socket to the TV, starts reading messages, and
    //        handles cleanup when disconnecting.
    // =========================================================================

    /**
     * Opens a TLS-encrypted socket to the Android TV and starts listening
     * for incoming protobuf messages.
     * Runs when the user selects this TV from the device list.
     * INPUT:  Nothing (uses the IP and PORT from class data).
     * OUTPUT: Calls listener.onConnected() on success, or
     *         listener.onError() on failure.  Starts a background read
     *         thread that processes incoming protobuf messages.
     */
    @Override
    public void connect() {
        intentionalStop = false;

        executor.submit(() -> {
            try {
                socket = (SSLSocket) SslUtils.permissiveSocketFactory().createSocket();
                socket.setSoTimeout(30000);
                socket.connect(new InetSocketAddress(ip, PORT), 5000);
                socket.startHandshake();

                in  = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                connected.set(true);
                if (listener != null) listener.onConnected();

                sendCapabilityRequest();

            } catch (Exception e) {
                Log.e(TAG, "Connection failed", e);
                if (!intentionalStop) {
                    connected.set(false);
                    if (listener != null)
                        listener.onError("Connection failed: " + e.getMessage());
                }
                return;
            }

            // Read loop on a dedicated thread - executor must stay free for commands
            readThread = new Thread(() -> {
                while (connected.get() && !intentionalStop) {
                    try {
                        int len = in.readInt();
                        if (len > 0 && len < 65536) {
                            byte[] data = new byte[len];
                            in.readFully(data);
                            try {
                                handleResponse(RemoteProto.RemoteMessage.parseFrom(data));
                            } catch (Exception e) {
                                Log.w(TAG, "Failed to parse response", e);
                            }
                        }
                    } catch (java.net.SocketTimeoutException ignored) {
                    } catch (IOException e) {
                        if (!intentionalStop) {
                            connected.set(false);
                            if (listener != null)
                                listener.onDisconnected("Connection lost: " + e.getMessage());
                        }
                        break;
                    }
                }
            }, "AndroidTvReadLoop");
            readThread.start();
        });
    }

    /**
     * Processes a protobuf response from the TV, such as capability info.
     * Runs on the read thread whenever a complete message is received.
     * INPUT:  response - a decoded protobuf RemoteMessage from the TV.
     * OUTPUT: Logs the TV's capabilities (mouse, keyboard, media support).
     */
    private void handleResponse(RemoteProto.RemoteMessage response) {
        if (response.hasCapabilityResponse()) {
            RemoteProto.CapabilityResponse cap = response.getCapabilityResponse();
            Log.d(TAG, "TV capabilities - mouse:" + cap.getSupportsMouse()
                + " keyboard:" + cap.getSupportsKeyboard()
                + " media:" + cap.getSupportsMedia());
        }
    }

    /**
     * Asks the TV what features it supports (mouse, keyboard, media, system).
     * Runs right after the connection is established (called from connect()).
     * INPUT:  Nothing.
     * OUTPUT: Sends a protobuf request; the reply arrives in handleResponse().
     */
    private void sendCapabilityRequest() {
        if (!isReady()) return;
        try {
            writeMessage(RemoteProto.RemoteMessage.newBuilder()
                .setCapabilityRequest(RemoteProto.CapabilityRequest.newBuilder()
                    .setWantMouse(true)
                    .setWantKeyboard(true)
                    .setWantMedia(true)
                    .setWantSystem(true)
                    .build())
                .build());
        } catch (IOException e) {
            Log.e(TAG, "sendCapabilityRequest failed", e);
        }
    }

    /**
     * Closes the connection to the Android TV and cleans up all threads.
     * Runs when the user navigates away or the app shuts down.
     * INPUT:  Nothing.
     * OUTPUT: Socket is closed, threads are stopped, state is reset.
     */
    @Override
    public void disconnect() {
        intentionalStop = true;
        connected.set(false);
        if (readThread != null) {
            readThread.interrupt();
            readThread = null;
        }
        try {
            if (socket != null) {
                socket.close();
                socket = null;
            }
        } catch (IOException e) {
            Log.w(TAG, "Error closing socket", e);
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                Log.w(TAG, "Executor did not terminate in time");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** Checks whether the socket is connected and ready to send data. */
    private boolean isReady() {
        return connected.get() && out != null;
    }

    /**
     * Writes a protobuf message to the TV: first the length (4 bytes,
     * big-endian), then the raw protobuf bytes.
     * INPUT:  msg - the protobuf RemoteMessage to send.
     * OUTPUT: Nothing; throws IOException on failure.
     */
    private synchronized void writeMessage(RemoteProto.RemoteMessage msg) throws IOException {
        byte[] data = msg.toByteArray();
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    // =========================================================================
    // SECTION: KEY SENDING
    // WHAT:  Sends a remote-control key press to the Android TV using a
    //        protobuf key message.  Supports both numeric key codes and
    //        string-based key names.
    // The proto field is key_value (field 1) - setKeyValue() in generated code.
    // Removed the fragile reflection approach.
    // =========================================================================

    /**
     * Sends a remote-control key press using a numeric Android key code.
     * INPUT:  keyCode   - the Android key code (e.g. 19 = DPAD_UP).
     *         longPress - true to hold the button down.
     * OUTPUT: Nothing - sends a protobuf key message to the TV.
     */
    @Override
    public void sendKey(int keyCode, boolean longPress) {
        if (!isReady()) return;
        executor.submit(() -> {
            try {
                writeMessage(RemoteProto.RemoteMessage.newBuilder()
                    .setKey(RemoteProto.RemoteKeyCode.newBuilder()
                        .setKeyValue(keyCode)
                        .setLongPress(longPress)
                        .build())
                    .build());
            } catch (IOException e) {
                Log.e(TAG, "sendKey failed", e);
            }
        });
    }

    /**
     * Sends a remote-control key press by name (e.g. "KEY_UP", "KEY_HOME").
     * Looks up the numeric key code in mapKeyCode(), then sends a short press.
     * INPUT:  keyCode - string name of the key.
     * OUTPUT: Nothing.
     */
    @Override
    public void sendKey(String keyCode) {
        int code = mapKeyCode(keyCode);
        if (code != 0) sendKey(code, false);
    }

    // =========================================================================
    // SECTION: KEY CODE MAP
    // WHAT:  Converts human-readable key names (like "KEY_UP", "KEY_HOME")
    //        into Android AOSP keycode numbers that the TV understands.
    // =========================================================================

    /**
     * Converts a key name string (e.g. "KEY_UP") to an Android key code number.
     * INPUT:  keyCode - string like "KEY_HOME", "KEY_VOLUMEUP", etc.
     * OUTPUT: The matching Android key code integer, or 0 if the name is unknown.
     */
    private int mapKeyCode(String keyCode) {
        switch (keyCode) {
            // Navigation
            case "KEY_UP":        return 19;
            case "KEY_DOWN":      return 20;
            case "KEY_LEFT":      return 21;
            case "KEY_RIGHT":     return 22;
            case "KEY_ENTER":     return 23;
            case "KEY_BACK":
            case "KEY_RETURN":    return 4;
            case "KEY_HOME":      return 3;
            case "KEY_MENU":      return 82;
            // Volume
            case "KEY_VOLUMEUP":
            case "KEY_VOLUP":     return 24;
            case "KEY_VOLUMEDOWN":
            case "KEY_VOLDOWN":   return 25;
            case "KEY_MUTE":      return 164;
            // System
            case "KEY_POWER":     return 26;
            case "KEY_INFO":      return 165;
            case "KEY_GUIDE":     return 187;
            case "KEY_SOURCE":    return 178;
            // Channel
            case "KEY_CHUP":      return 166;
            case "KEY_CHDOWN":    return 167;
            // Digits
            case "KEY_0":         return 7;
            case "KEY_1":         return 8;
            case "KEY_2":         return 9;
            case "KEY_3":         return 10;
            case "KEY_4":         return 11;
            case "KEY_5":         return 12;
            case "KEY_6":         return 13;
            case "KEY_7":         return 14;
            case "KEY_8":         return 15;
            case "KEY_9":         return 16;
            // Media
            case "KEY_PLAYPAUSE": return 85;
            case "KEY_STOP":      return 86;
            case "KEY_REWIND":    return 89;   // KEYCODE_MEDIA_REWIND
            case "KEY_FF":        return 90;   // KEYCODE_MEDIA_FAST_FORWARD
            case "KEY_PREV":      return 88;   // KEYCODE_MEDIA_PREVIOUS
            case "KEY_NEXT":      return 87;   // KEYCODE_MEDIA_NEXT
            default:
                Log.w(TAG, "Unknown key code: " + keyCode);
                return 0;
        }
    }

    // =========================================================================
    // SECTION: TEXT INPUT
    // WHAT:  Sends text to the TV.  First tries using Google's keyboard-input
    //        protobuf message (requires a text field focused on the TV).
    //        If that fails, falls back to sending each character as individual
    //        Android key events (like typing on a keyboard).
    // =========================================================================

    /**
     * Sends text to the TV (e.g. for search boxes or text fields).
     * First tries the keyboard-input protobuf message; if that fails, falls
     * back to sending each character as individual key presses.
     * INPUT:  text       - the string to type.
     *         replaceAll - true to replace existing text, false to append.
     * OUTPUT: Nothing - sends protobuf messages to the TV.
     */
    @Override
    public void sendText(String text, boolean replaceAll) {
        if (!isReady() || text == null || text.isEmpty()) return;

        executor.submit(() -> {
            try {
                // Primary - KeyboardInput proto
                writeMessage(RemoteProto.RemoteMessage.newBuilder()
                    .setKeyboard(RemoteProto.KeyboardInput.newBuilder()
                        .setText(text)
                        .setReplaceAll(replaceAll)
                        .setSubmit(false)
                        .build())
                    .build());
                Log.d(TAG, "sendText (proto): " + text);
            } catch (IOException e) {
                Log.w(TAG, "KeyboardInput proto failed, falling back to keycodes", e);
                sendTextAsKeycodes(text);
            }
        });
    }

    /**
     * Fallback: sends each character as an individual AOSP keycode event.
     * Handles a-z, A-Z (with shift), 0-9, space, and common punctuation.
     * Called on executor thread - no extra submit needed.
     * INPUT:  text - the string to type out character by character.
     * OUTPUT: Nothing - sends multiple protobuf key messages.
     */
    private void sendTextAsKeycodes(String text) {
        for (char c : text.toCharArray()) {
            int[] codes = charToKeycodes(c); // [keycode] or [SHIFT=59, keycode]
            for (int code : codes) {
                if (code == 0) continue;
                try {
                    writeMessage(RemoteProto.RemoteMessage.newBuilder()
                        .setKey(RemoteProto.RemoteKeyCode.newBuilder()
                            .setKeyValue(code)
                            .setLongPress(false)
                            .build())
                        .build());
                    Thread.sleep(30); // small inter-key gap
                } catch (IOException | InterruptedException e) {
                    Log.e(TAG, "sendTextAsKeycodes error at char=" + c, e);
                    return;
                }
            }
        }
    }

    /** Android keycode for the SHIFT key, used for uppercase and shifted symbols. */
    private static final int SHIFT = 59;

    /**
     * Maps a single character to one or two AOSP keycodes.
     * Stores lowercase letters as a single code, uppercase letters as
     * [SHIFT, letterCode], and common symbols with their keycodes.
     */
    private static final Map<Character, int[]> CHAR_MAP = new HashMap<>();
    static {
        for (int i = 0; i < 26; i++) {
            CHAR_MAP.put((char)('a' + i), new int[]{29 + i});
            CHAR_MAP.put((char)('A' + i), new int[]{SHIFT, 29 + i});
        }
        for (int i = 0; i < 10; i++) {
            CHAR_MAP.put((char)('0' + i), new int[]{7 + i});
        }
        CHAR_MAP.put(' ',  new int[]{62});
        CHAR_MAP.put('\n', new int[]{66});
        CHAR_MAP.put('.',  new int[]{56});
        CHAR_MAP.put(',',  new int[]{55});
        CHAR_MAP.put('-',  new int[]{69});
        CHAR_MAP.put('=',  new int[]{70});
        CHAR_MAP.put('[',  new int[]{71});
        CHAR_MAP.put(']',  new int[]{72});
        CHAR_MAP.put('\\', new int[]{73});
        CHAR_MAP.put(';',  new int[]{74});
        CHAR_MAP.put('\'', new int[]{75});
        CHAR_MAP.put('`',  new int[]{68});
        CHAR_MAP.put('/',  new int[]{76});
        CHAR_MAP.put('@',  new int[]{SHIFT, 9});
        CHAR_MAP.put('#',  new int[]{SHIFT, 10});
        CHAR_MAP.put('!',  new int[]{SHIFT, 8});
        CHAR_MAP.put('?',  new int[]{SHIFT, 76});
        CHAR_MAP.put('_',  new int[]{SHIFT, 69});
        CHAR_MAP.put('+',  new int[]{SHIFT, 70});
        CHAR_MAP.put(':',  new int[]{SHIFT, 74});
        CHAR_MAP.put('"',  new int[]{SHIFT, 75});
    }

    /**
     * Maps a character to one or two AOSP keycodes.
     * Returns a single-element array for unshifted chars,
     * or [KEYCODE_SHIFT_LEFT=59, keycode] for uppercase / shifted symbols.
     * INPUT:  c - the character to look up.
     * OUTPUT: int[] of 1-2 keycode numbers, or [0] if the character has no mapping.
     */
    private int[] charToKeycodes(char c) {
        int[] result = CHAR_MAP.get(c);
        if (result == null) {
            Log.w(TAG, "No keycode mapping for char: " + c);
            return new int[]{0};
        }
        return result;
    }

    // =========================================================================
    // SECTION: MOUSE / TOUCHPAD
    // WHAT:  Sends mouse movement, clicks, scroll-wheel, and activation
    //        events to the Android TV using protobuf messages.
    // =========================================================================

    /**
     * Moves the mouse cursor by a relative amount (delta from current position).
     * INPUT:  dx - pixels to move horizontally (positive = right).
     *         dy - pixels to move vertically (positive = down).
     * OUTPUT: Nothing - sends a protobuf mouse message.
     */
    @Override
    public void sendMouseMove(int dx, int dy) {
        if (!isReady()) return;
        executor.submit(() -> {
            try {
                writeMessage(RemoteProto.RemoteMessage.newBuilder()
                    .setMouse(RemoteProto.MouseInput.newBuilder()
                        .setDx(dx)
                        .setDy(dy)
                        .build())
                    .build());
            } catch (IOException e) {
                Log.e(TAG, "sendMouseMove failed", e);
            }
        });
    }

    /**
     * Clicks a mouse button (left, right, or middle).
     * INPUT:  button - "left", "right", or "middle".
     * OUTPUT: Nothing - sends a protobuf mouse message.
     */
    @Override
    public void sendMouseClick(String button) {
        if (!isReady()) return;

        RemoteProto.MouseInput.Button btn;
        switch (button.toLowerCase()) {
            case "right":  btn = RemoteProto.MouseInput.Button.RIGHT;  break;
            case "middle": btn = RemoteProto.MouseInput.Button.MIDDLE; break;
            default:       btn = RemoteProto.MouseInput.Button.LEFT;   break;
        }

        final RemoteProto.MouseInput.Button finalBtn = btn;
        executor.submit(() -> {
            try {
                writeMessage(RemoteProto.RemoteMessage.newBuilder()
                    .setMouse(RemoteProto.MouseInput.newBuilder()
                        .setButton(finalBtn)
                        .build())
                    .build());
            } catch (IOException e) {
                Log.e(TAG, "sendMouseClick failed", e);
            }
        });
    }

    /**
     * Scrolls the mouse wheel vertically.
     * INPUT:  deltaY - positive = scroll down, negative = scroll up.
     * OUTPUT: Nothing - sends a protobuf mouse message.
     */
    @Override
    public void sendMouseWheel(int deltaY) {
        if (!isReady()) return;
        executor.submit(() -> {
            try {
                writeMessage(RemoteProto.RemoteMessage.newBuilder()
                    .setMouse(RemoteProto.MouseInput.newBuilder()
                        .setWheelY(deltaY)
                        .build())
                    .build());
            } catch (IOException e) {
                Log.e(TAG, "sendMouseWheel failed", e);
            }
        });
    }

    /** Sends a zero-movement mouse event to wake up or activate the TV screen. */
    @Override
    public void sendMouseActivate() {
        sendMouseMove(0, 0);
    }

    // =========================================================================
    // SECTION: APP LAUNCH
    // WHAT:  Launches an Android app on the TV by its package name.
    // =========================================================================

    /**
     * Launches an app on the TV by its Android package name (e.g. "com.netflix.ninja").
     * INPUT:  appId - the Android package name of the app to launch.
     * OUTPUT: Nothing - sends a protobuf app-link message.
     */
    @Override
    public void sendAppLaunch(String appId) {
        if (!isReady()) return;
        executor.submit(() -> {
            try {
                writeMessage(RemoteProto.RemoteMessage.newBuilder()
                    .setApp(RemoteProto.RemoteAppLinkLaunchRequest.newBuilder()
                        .setAppLink(appId)
                        .build())
                    .build());
            } catch (IOException e) {
                Log.e(TAG, "sendAppLaunch failed", e);
            }
        });
    }

    // =========================================================================
    // SECTION: VOLUME / SYSTEM CONTROLS
    // WHAT:  Shortcut methods for common TV remote buttons (volume, home,
    //        back, power).  Each calls sendKey() with the right key code.
    // =========================================================================

    /** Sends the volume-up command. */
    @Override public void sendVolumeUp()   { sendKey(24, false); }
    /** Sends the volume-down command. */
    @Override public void sendVolumeDown() { sendKey(25, false); }
    /** Sends the mute toggle command. */
    @Override public void sendMute()       { sendKey(164, false); }
    /** Sends the HOME button command. */
    @Override public void sendHome()       { sendKey(3, false); }
    /** Sends the BACK button command. */
    @Override public void sendBack()       { sendKey(4, false); }
    /** Sends the POWER button command. */
    @Override public void sendPower()      { sendKey(26, false); }

    // =========================================================================
    // SECTION: MEDIA TRANSPORT CONTROLS
    // WHAT:  Shortcut methods for media playback buttons (play/pause, stop,
    //        skip track, rewind, fast forward).  Each calls sendKey() with
    //        the appropriate Android media key code.
    // =========================================================================

    /** Sends the PLAY/PAUSE media command. */
    @Override public void sendMediaPlayPause() { sendKey(85, false); }  // KEYCODE_MEDIA_PLAY_PAUSE
    /** Sends the STOP media command. */
    @Override public void sendMediaStop()      { sendKey(86, false); }  // KEYCODE_MEDIA_STOP
    /** Sends the PREVIOUS TRACK command. */
    @Override public void sendMediaPrev()      { sendKey(88, false); }  // KEYCODE_MEDIA_PREVIOUS
    /** Sends the NEXT TRACK command. */
    @Override public void sendMediaNext()      { sendKey(87, false); }  // KEYCODE_MEDIA_NEXT
    /** Sends the REWIND command. */
    @Override public void sendMediaRW()        { sendKey(89, false); }  // KEYCODE_MEDIA_REWIND
    /** Sends the FAST FORWARD command. */
    @Override public void sendMediaFF()        { sendKey(90, false); }  // KEYCODE_MEDIA_FAST_FORWARD

}
