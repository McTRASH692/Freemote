package com.mctrash692.freemote.remote.androidtv;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.mctrash692.freemote.remote.androidtv.proto.PairingProto;
import com.google.protobuf.ByteString;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;

public class PairingManager {

    private static final String TAG = "PairingManager";
    private static final int PORT = 6467;

    private static final String PREFS = "freemote_androidtv";
    private static final String KEY_CRED = "cred_";

    public interface Listener {
        void onPairingStarted();
        void onSecretRequired();
        void onPaired(String sessionId);
        void onError(String message);
    }

    private enum State {
        IDLE, CONNECTING, WAITING_ACK, WAITING_SECRET, COMPLETE, ERROR
    }

    private final Context context;
    private final String ip;
    private final String savedCredentials;
    private final Listener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SSLSocket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private X509Certificate remoteCert;
    private KeyPair keyPair;

    private volatile State state = State.IDLE;

    public PairingManager(Context context, String ip, String savedCredentials, Listener listener) {
        this.context = context;
        this.ip = ip;
        this.savedCredentials = savedCredentials;
        this.listener = listener;
    }

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

    public void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}

        executor.shutdownNow();
        state = State.IDLE;
    }

    /* ---------------- protocol ---------------- */

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

    /* ---------------- wire ---------------- */

    private void write(PairingProto.PairingMessage msg) throws IOException {
        byte[] data = msg.toByteArray();
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

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

    /* ---------------- crypto ---------------- */

    private byte[] deriveSecret(String pin) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");

        sha.update(keyPair.getPublic().getEncoded());
        sha.update(remoteCert.getPublicKey().getEncoded());
        sha.update(pin.getBytes("UTF-8"));

        return sha.digest();
    }

    /* ---------------- ssl ---------------- */

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

    /* ---------------- storage ---------------- */

    private void save(String sessionId) {
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        prefs.edit().putString(KEY_CRED + ip, sessionId).apply();
    }

    private void fail(String msg) {
        state = State.ERROR;
        Log.e(TAG, msg);
        listener.onError(msg);
    }
}
