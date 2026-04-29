package com.mctrash692.freemote.remote.androidtv;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.mctrash692.freemote.remote.androidtv.proto.PairingProto;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.x509.X509V3CertificateGenerator;

public class PairingManager {

    private static final String TAG      = "PairingManager";
    private static final int    PORT     = 6467;
    private static final String PREFS    = "freemote_androidtv";
    private static final String KEY_CERT = "cert_";

    public interface Listener {
        void onPairingStarted();
        void onSecretRequired();
        void onPaired(String credentials);
        void onError(String message);
    }

    private final Context         context;
    private final String          ip;
    private final String          savedCredentials;
    private final Listener        listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SSLSocket        socket;
    private DataInputStream  in;
    private DataOutputStream out;
    private X509Certificate  localCert;
    private X509Certificate  remoteCert;
    private String           pendingPin;

    public PairingManager(Context context, String ip, String savedCredentials, Listener listener) {
        this.context          = context;
        this.ip               = ip;
        this.savedCredentials = savedCredentials;
        this.listener         = listener;
    }

    public void startPairing() {
        executor.submit(() -> {
            try {
                // Generate local RSA keypair and self-signed cert for the Polo handshake
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048, new SecureRandom());
                KeyPair keyPair = kpg.generateKeyPair();
                localCert = generateSelfSignedCert(keyPair);

                SSLContext sslContext = buildPermissiveSslContext();
                socket = (SSLSocket) sslContext.getSocketFactory().createSocket();
                socket.connect(new InetSocketAddress(ip, PORT), 5000);
                socket.startHandshake();

                in  = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
                sendPairingRequest();
                listener.onPairingStarted();

                PairingProto.PairingMessage ack = readMessage();
                if (ack == null || !ack.hasPairingRequestAck()) {
                    listener.onError("No pairing request ack from TV");
                    return;
                }

                sendOptions();

                PairingProto.PairingMessage optAck = readMessage();
                if (optAck == null || !optAck.hasOptionAck()) {
                    listener.onError("No option ack from TV");
                    return;
                }

                sendConfiguration();

                PairingProto.PairingMessage cfgAck = readMessage();
                if (cfgAck == null || !cfgAck.hasConfigurationAck()) {
                    listener.onError("No configuration ack from TV");
                    return;
                }

                listener.onSecretRequired();

            } catch (Exception e) {
                Log.e(TAG, "Pairing error", e);
                listener.onError(e.getMessage());
            }
        });
    }

    public void submitPin(String pin) {
        this.pendingPin = pin;
        executor.submit(() -> {
            try {
                byte[] secret = deriveSecret(pin);
                sendSecret(secret);

                PairingProto.PairingMessage secretAck = readMessage();
                if (secretAck != null && secretAck.hasSecretAck()) {
                    String credentials = ip + ":" + PORT;
                    saveCredentials(credentials);
                    listener.onPaired(credentials);
                } else {
                    listener.onError("Pairing failed — wrong PIN?");
                }
            } catch (Exception e) {
                Log.e(TAG, "PIN submission error", e);
                listener.onError(e.getMessage());
            }
        });
    }

    public void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        executor.shutdown();
    }

    // --- Self-signed cert generation ---

    private X509Certificate generateSelfSignedCert(KeyPair keyPair) throws Exception {
        X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();
        certGen.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
        X500Principal subject = new X500Principal("CN=Freemote");
        certGen.setIssuerDN(subject);
        certGen.setSubjectDN(subject);
        certGen.setNotBefore(new Date(System.currentTimeMillis() - 86400000L));
        certGen.setNotAfter(new Date(System.currentTimeMillis() + 365L * 86400000L));
        certGen.setPublicKey(keyPair.getPublic());
        certGen.setSignatureAlgorithm("SHA256WithRSAEncryption");
        return certGen.generate(keyPair.getPrivate(), new SecureRandom());
    }

    // --- Protocol message builders ---

    private void sendPairingRequest() throws Exception {
        PairingProto.PairingMessage msg = PairingProto.PairingMessage.newBuilder()
            .setPairingRequest(
                PairingProto.PairingRequestMessage.newBuilder()
                    .setServiceName("androidtvremote")
                    .setClientName("Freemote")
                    .build()
            ).build();
        writeMessage(msg);
    }

    private void sendOptions() throws Exception {
        PairingProto.EncodingOption encoding = PairingProto.EncodingOption.newBuilder()
            .setType(PairingProto.EncodingType.ENCODING_TYPE_NUMERIC)
            .setSymbolLength(6)
            .build();

        PairingProto.PairingMessage msg = PairingProto.PairingMessage.newBuilder()
            .setOptions(
                PairingProto.OptionsMessage.newBuilder()
                    .addInputEncodings(encoding)
                    .setPreferredRole(PairingProto.RoleType.ROLE_TYPE_INPUT)
                    .build()
            ).build();
        writeMessage(msg);
    }

    private void sendConfiguration() throws Exception {
        PairingProto.EncodingOption encoding = PairingProto.EncodingOption.newBuilder()
            .setType(PairingProto.EncodingType.ENCODING_TYPE_NUMERIC)
            .setSymbolLength(6)
            .build();

        PairingProto.PairingMessage msg = PairingProto.PairingMessage.newBuilder()
            .setConfiguration(
                PairingProto.ConfigurationMessage.newBuilder()
                    .setEncoding(encoding)
                    .setClientRole(PairingProto.RoleType.ROLE_TYPE_INPUT)
                    .build()
            ).build();
        writeMessage(msg);
    }

    private void sendSecret(byte[] secret) throws Exception {
        PairingProto.PairingMessage msg = PairingProto.PairingMessage.newBuilder()
            .setSecret(
                PairingProto.SecretMessage.newBuilder()
                    .setSecret(com.google.protobuf.ByteString.copyFrom(secret))
                    .build()
            ).build();
        writeMessage(msg);
    }

    // --- Wire framing ---

    private void writeMessage(PairingProto.PairingMessage msg) throws IOException {
        byte[] data = msg.toByteArray();
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    private PairingProto.PairingMessage readMessage() throws IOException {
        int len = in.readInt();
        if (len <= 0 || len > 65536) return null;
        byte[] data = new byte[len];
        in.readFully(data);
        try {
            return PairingProto.PairingMessage.parseFrom(data);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse pairing message", e);
            return null;
        }
    }

    // --- Secret derivation (Polo protocol) ---

    private byte[] deriveSecret(String pin) throws Exception {
        byte[] clientMod = getCertModulus(localCert);
        byte[] serverMod = getCertModulus(remoteCert);
        byte[] pinBytes  = pin.getBytes("UTF-8");

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        sha256.update(clientMod);
        sha256.update(serverMod);
        sha256.update(pinBytes);
        return sha256.digest();
    }

    private byte[] getCertModulus(X509Certificate cert) throws Exception {
        java.security.interfaces.RSAPublicKey pub =
            (java.security.interfaces.RSAPublicKey) cert.getPublicKey();
        byte[] mod = pub.getModulus().toByteArray();
        if (mod[0] == 0) {
            byte[] stripped = new byte[mod.length - 1];
            System.arraycopy(mod, 1, stripped, 0, stripped.length);
            return stripped;
        }
        return mod;
    }

    // --- SSL context ---

    private SSLContext buildPermissiveSslContext() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    if (chain != null && chain.length > 0) {
                        remoteCert = chain[0];
                    }
                }
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAll, new SecureRandom());
        return ctx;
    }

    // --- Credential persistence ---

    private void saveCredentials(String credentials) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_CERT + ip, credentials).apply();
    }

    public static String loadCredentials(Context context, String ip) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString(KEY_CERT + ip, null);
    }
}
