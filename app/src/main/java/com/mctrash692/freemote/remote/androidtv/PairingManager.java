package com.mctrash692.freemote.remote.androidtv;

// ============================================================================
// FILE: PairingManager.java
// WHAT:  Manages the "pairing" process with Android TV / Google TV devices.
//        Pairing is like introducing the app to the TV for the first time.
//        The TV shows a 6-digit code on screen; the user types it into the
//        app to prove they have permission to control the TV.
//        This uses encrypted communication (SSL/TLS) and a special message
//        format called Protocol Buffers (a compact data format from Google).
// ============================================================================

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.mctrash692.freemote.remote.androidtv.proto.PairingProto;
import com.google.protobuf.ByteString;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.*;

public class PairingManager {

    // ==========================================================================
    // SECTION: CONSTANTS
    // WHAT:  Settings for pairing with Android TV / Google TV devices.
    //        The port (6467) is the standard Android TV pairing port.
    //        Saved credentials let the app skip pairing on future connections.
    // ==========================================================================

    // Used for logging messages so developers can see what's happening.
    private static final String TAG = "PairingManager";
    // Port number Android TVs use for the pairing process.
    private static final int PORT = 6467;

    // Storage name for saving pairing credentials.
    private static final String PREFS = "freemote_androidtv";
    // Storage key name for saving credentials per TV.
    private static final String KEY_CRED = "cred_";

    // ==========================================================================
    // SECTION: LISTENER INTERFACE
    // WHAT:  The app uses this to find out what's happening during pairing.
    //        Different methods are called at each step so the app can show
    //        the right screens (like "enter the PIN code").
    // ==========================================================================

    public interface Listener {
        void onPairingStarted();
        void onSecretRequired();
        void onPaired(String sessionId);
        void onError(String message);
    }

    // ==========================================================================
    // SECTION: STATE MACHINE
    // WHAT:  Tracks where we are in the pairing process. Each step moves us
    //        forward: Idle -> Connecting -> Waiting for acknowledgment ->
    //        Waiting for secret (PIN) -> Complete or Error.
    // ==========================================================================

    private enum State {
        IDLE, CONNECTING, WAITING_ACK, WAITING_SECRET, COMPLETE, ERROR
    }

    // ==========================================================================
    // SECTION: SETTINGS
    // WHAT:  Stores everything needed for the pairing process: the phone app
    //        context, the TV's address, saved credentials, the listener.
    // ==========================================================================

    // The Android app's information (needed to save credentials to storage).
    private final Context context;
    // The TV's network address (like "192.168.1.100").
    private final String ip;
    // Previously saved credentials (if any) for skipping re-pairing.
    private final String savedCredentials;
    // The app code that gets notified of each pairing step.
    private final Listener listener;
    // A single background thread for all network communication.
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // The encrypted connection to the TV (SSL/TLS socket).
    private SSLSocket socket;
    // Stream for reading data from the TV.
    private DataInputStream in;
    // Stream for sending data to the TV.
    private DataOutputStream out;

    // The TV's security certificate (used to verify identity).
    private X509Certificate remoteCert;
    // A pair of keys (public + private) for encryption.
    private KeyPair keyPair;

    // Current state of the pairing process (starts at IDLE).
    private volatile State state = State.IDLE;

    // ==========================================================================
    // METHOD: Constructor
    // WHAT:  Sets up the pairing manager for a specific Android TV.
    // INPUT: context          = the Android app (needed to save credentials)
    //        ip               = the TV's network address
    //        savedCredentials = previously-saved credentials (null if first time)
    //        listener         = the app code notified of each step
    // ==========================================================================

    public PairingManager(Context context, String ip, String savedCredentials, Listener listener) {
        this.context = context;
        this.ip = ip;
        this.savedCredentials = savedCredentials;
        this.listener = listener;
    }

    // ==========================================================================
    // METHOD: startPairing
    // WHAT:  Begins the pairing process with the Android TV. This:
    //        1. Generates a pair of encryption keys (RSA)
    //        2. Opens an encrypted connection to the TV
    //        3. Sends a pairing request identifying this app
    //        4. Negotiates options (like using a 6-digit numeric PIN)
    //        5. Waits for the user to enter the PIN shown on the TV
    // ==========================================================================

    public void startPairing() {
        state = State.CONNECTING;

        executor.submit(() -> {
            try {
                keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();

                SSLContext ctx = buildPermissiveSslContext();

                socket = (SSLSocket) ctx.getSocketFactory().createSocket();
                socket.setSoTimeout(15000);
                socket.connect(new InetSocketAddress(ip, PORT), 5000);
                socket.startHandshake();

                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                listener.onPairingStarted();

                sendPairingRequest();
                state = State.WAITING_ACK;

                PairingProto.PairingMessage ack = readMessage();
                if (ack == null || !ack.hasPairingRequestAck()) {
                    fail("pairing ack missing");
                    return;
                }

                sendOptions();

                PairingProto.PairingMessage opt = readMessage();
                if (opt == null || !opt.hasOptionAck()) {
                    fail("option ack missing");
                    return;
                }

                sendConfiguration();

                PairingProto.PairingMessage cfg = readMessage();
                if (cfg == null || !cfg.hasConfigurationAck()) {
                    fail("config ack missing");
                    return;
                }

                state = State.WAITING_SECRET;
                listener.onSecretRequired();

            } catch (Exception e) {
                fail(e.getMessage());
            }
        });
    }

    // ==========================================================================
    // METHOD: submitPin
    // WHAT:  Sends the PIN code (shown on the TV screen) to the TV to complete
    //        pairing. The PIN is combined with the encryption keys to create a
    //        secret that proves this app is allowed to control the TV.
    // INPUT: pin = the 6-digit number shown on the TV screen
    // ==========================================================================

    public void submitPin(String pin) {
        executor.submit(() -> {
            try {
                if (remoteCert == null) {
                    fail("missing remote cert");
                    return;
                }

                byte[] secret = deriveSecret(pin);
                sendSecret(secret);

                PairingProto.PairingMessage resp = readMessage();

                if (resp != null && resp.hasSecretAck()) {
                    String sessionId = ip + ":" + System.currentTimeMillis();
                    save(sessionId);
                    state = State.COMPLETE;
                    listener.onPaired(sessionId);
                } else {
                    fail("secret rejected");
                }

            } catch (Exception e) {
                fail(e.getMessage());
            }
        });
    }

    // ==========================================================================
    // METHOD: disconnect
    // WHAT:  Cancels the pairing process and closes the connection. Resets the
    //        state back to idle.
    // ==========================================================================

    public void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}

        executor.shutdownNow();
        state = State.IDLE;
    }

    // ==========================================================================
    // SECTION: PROTOCOL
    // WHAT:  These methods handle the step-by-step conversation with the TV
    //        during pairing. Each method sends one message and waits for the
    //        TV's response before moving to the next step.
    // ==========================================================================

    // ==========================================================================
    // METHOD: sendPairingRequest
    // WHAT:  Sends the first message to the TV, introducing this app by name
    //        ("freemote") and saying it's from an Android device.
    // ==========================================================================

    private void sendPairingRequest() throws Exception {
        PairingProto.PairingMessage msg =
                PairingProto.PairingMessage.newBuilder()
                        .setPairingRequest(
                                PairingProto.PairingRequestMessage.newBuilder()
                                        .setServiceName("freemote")
                                        .setClientName("android")
                                        .build()
                        ).build();
        write(msg);
    }

    // ==========================================================================
    // METHOD: sendOptions
    // WHAT:  Tells the TV what kind of PIN code we want to use. This requests
    //        a numeric 6-digit code (like the ones you see on streaming apps).
    //        It also says the user will type the code into the phone (not the
    //        other way around).
    // ==========================================================================

    private void sendOptions() throws Exception {
        PairingProto.EncodingOption enc =
                PairingProto.EncodingOption.newBuilder()
                        .setType(PairingProto.EncodingType.ENCODING_TYPE_NUMERIC)
                        .setSymbolLength(6)
                        .build();

        PairingProto.PairingMessage msg =
                PairingProto.PairingMessage.newBuilder()
                        .setOptions(
                                PairingProto.OptionsMessage.newBuilder()
                                        .addInputEncodings(enc)
                                        .setPreferredRole(PairingProto.RoleType.ROLE_TYPE_INPUT)
                                        .build()
                        ).build();
        write(msg);
    }

    // ==========================================================================
    // METHOD: sendConfiguration
    // WHAT:  Confirms the pairing settings with the TV (6-digit numeric PIN,
    //        user types it on the phone). After this, the TV shows the PIN
    //        on screen and waits for the user to enter it.
    // ==========================================================================

    private void sendConfiguration() throws Exception {
        PairingProto.EncodingOption enc =
                PairingProto.EncodingOption.newBuilder()
                        .setType(PairingProto.EncodingType.ENCODING_TYPE_NUMERIC)
                        .setSymbolLength(6)
                        .build();

        PairingProto.PairingMessage msg =
                PairingProto.PairingMessage.newBuilder()
                        .setConfiguration(
                                PairingProto.ConfigurationMessage.newBuilder()
                                        .setEncoding(enc)
                                        .setClientRole(PairingProto.RoleType.ROLE_TYPE_INPUT)
                                        .build()
                        ).build();
        write(msg);
    }

    // ==========================================================================
    // METHOD: sendSecret
    // WHAT:  Sends the computed secret (derived from the PIN + encryption keys)
    //        to the TV. If the secret matches what the TV expects, pairing is
    //        successful and the app can control the TV.
    // ==========================================================================

    private void sendSecret(byte[] secret) throws Exception {
        PairingProto.PairingMessage msg =
                PairingProto.PairingMessage.newBuilder()
                        .setSecret(
                                PairingProto.SecretMessage.newBuilder()
                                        .setSecret(ByteString.copyFrom(secret))
                                        .build()
                        ).build();
        write(msg);
    }

    // ==========================================================================
    // SECTION: WIRE (SENDING & RECEIVING DATA)
    // WHAT:  These methods handle the low-level reading and writing of data
    //        over the network connection. Messages are sent with a 4-byte
    //        length prefix so the receiver knows how much data to expect.
    // ==========================================================================

    // ==========================================================================
    // METHOD: write
    // WHAT:  Sends a message to the TV. First writes the message's length (so
    //        the TV knows how much data to expect), then writes the message
    //        itself as a packed binary format.
    // ==========================================================================

    private void write(PairingProto.PairingMessage msg) throws IOException {
        byte[] data = msg.toByteArray();
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    // ==========================================================================
    // METHOD: readMessage
    // WHAT:  Reads a message sent by the TV. First reads the message length,
    //        then reads that many bytes and converts them from the packed
    //        binary format back into a structured message.
    // ==========================================================================

    private PairingProto.PairingMessage readMessage() throws IOException {
        int len = in.readInt();
        if (len <= 0 || len > 65536) return null;

        byte[] buf = new byte[len];
        in.readFully(buf);

        try {
            return PairingProto.PairingMessage.parseFrom(buf);
        } catch (Exception e) {
            Log.e(TAG, "parse error", e);
            return null;
        }
    }

    // ==========================================================================
    // SECTION: CRYPTOGRAPHY
    // WHAT:  Creates a secret code by mixing together the app's public key,
    //        the TV's public key, and the PIN the user entered. This proves
    //        to the TV that the user saw and typed the correct PIN.
    // ==========================================================================

    // ==========================================================================
    // METHOD: deriveSecret
    // WHAT:  Combines the app's encryption key, the TV's encryption key, and
    //        the PIN code using a hash function (SHA-256). The result is a
    //        unique secret that only this app and this TV can create.
    // INPUT: pin = the 6-digit code shown on the TV screen
    // ==========================================================================

    private byte[] deriveSecret(String pin) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");

        sha.update(keyPair.getPublic().getEncoded());
        sha.update(remoteCert.getPublicKey().getEncoded());
        sha.update(pin.getBytes("UTF-8"));

        return sha.digest();
    }

    // ==========================================================================
    // SECTION: SSL/TLS SETUP
    // WHAT:  Creates an encrypted connection to the Android TV. It accepts any
    //        security certificate from the TV (even self-signed ones) so the
    //        connection works with any Android TV device. The TV's certificate
    //        is captured so it can be used in the secret derivation.
    // ==========================================================================

    // ==========================================================================
    // METHOD: buildPermissiveSslContext
    // WHAT:  Sets up an encrypted connection that trusts ANY TV certificate.
    //        This is needed because Android TVs use self-signed certificates
    //        that wouldn't be trusted by default. While capturing the TV's
    //        certificate, it saves it for use in the pairing math.
    // ==========================================================================

    private SSLContext buildPermissiveSslContext() throws Exception {

        TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {
                        if (c != null && c.length > 0) remoteCert = c[0];
                    }
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAll, new java.security.SecureRandom());
        return ctx;
    }

    // ==========================================================================
    // SECTION: STORAGE
    // WHAT:  Saves the pairing credentials to the phone so the app doesn't
    //        need to go through the pairing process again next time.
    // ==========================================================================

    // ==========================================================================
    // METHOD: save
    // WHAT:  Stores the pairing session ID in the phone's private storage
    //        so future connections can skip the PIN pairing step.
    // ==========================================================================

    private void save(String sessionId) {
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        prefs.edit().putString(KEY_CRED + ip, sessionId).apply();
    }

    // ==========================================================================
    // METHOD: fail
    // WHAT:  Handles an error during pairing. Sets the state to ERROR and
    //        notifies the app with an error message so it can show the user.
    // ==========================================================================

    private void fail(String msg) {
        state = State.ERROR;
        Log.e(TAG, msg);
        listener.onError(msg);
    }
}
