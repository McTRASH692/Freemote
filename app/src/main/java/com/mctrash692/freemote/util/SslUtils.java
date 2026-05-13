package com.mctrash692.freemote.util;

// ============================================================================
// FILE: SslUtils.java
// WHAT:  Helps the app talk to TVs over secure connections. Some TVs use
//        self-signed security certificates that would normally cause errors.
//        This file makes the app trust those connections anyway, so you can
//        still control your TV.
// WHY:   TVs often don't have "proper" security certificates like websites
//        do. Without this file, secure connections to many TVs would fail.
// ============================================================================

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

public final class SslUtils {

    // ==========================================================================
    // CONSTANTS
    // WHAT:  Special trust rules that accept any security certificate.
    //        Normally apps reject "self-signed" certificates, but TVs use
    //        them, so we must allow them.
    // ==========================================================================

    // A trust manager that says "yes" to every certificate, no questions asked
    private static final X509TrustManager PERMISSIVE_TM = new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] c, String a) {}
        public void checkServerTrusted(X509Certificate[] c, String a) {}
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    };

    // A hostname verifier that says "yes" to every address, no matter what name it uses
    private static final HostnameVerifier PERMISSIVE_HV = (hostname, session) -> true;

    // Private constructor prevents anyone from creating an instance of this utility class
    private SslUtils() {}

    // ==========================================================================
    // METHOD: permissiveTrustManager
    // WHAT:  Returns the "trust everything" certificate checker. This tells
    //        the app to accept any security certificate from the TV, even
    //        if it's self-signed or expired.
    // OUTPUT: the trust manager that accepts all certificates
    // ==========================================================================

    // ==========================================================================
    // METHOD: permissiveHostnameVerifier
    // WHAT:  Returns a hostname checker that says "yes" to every TV,
    //        regardless of what name the TV claims to have. Normally the app
    //        would check that the certificate matches the TV's address.
    // OUTPUT: the verifier that accepts all hostnames
    // ==========================================================================

    public static HostnameVerifier permissiveHostnameVerifier() {
        return PERMISSIVE_HV;
    }

    // ==========================================================================
    // METHOD: permissiveSslContext
    // WHAT:  Creates a fully configured SSL setup that trusts all certificates.
    //        This is the core piece that makes secure connections to TVs work
    //        despite their non-standard certificates.
    // OUTPUT: an SSL context ready to use for secure connections
    // ==========================================================================

    public static SSLContext permissiveSslContext() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{PERMISSIVE_TM}, new SecureRandom());
            return ctx;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to create permissive SSL context", e);
        }
    }

    // ==========================================================================
    // METHOD: permissiveSocketFactory
    // WHAT:  Creates a socket factory that produces secure (SSL) connections
    //        using our permissive settings. Sockets are the tunnels apps use
    //        to send data to devices.
    // OUTPUT: a socket factory for making SSL connections to TVs
    // ==========================================================================

    public static SSLSocketFactory permissiveSocketFactory() {
        return permissiveSslContext().getSocketFactory();
    }

    // ==========================================================================
    // METHOD: applyToOkHttp
    // WHAT:  Configures an OkHttp client (the app's network connection tool)
    //        to use our permissive SSL settings so it can talk to TVs without
    //        certificate errors.
    // INPUT: builder = the OkHttp client builder that needs SSL configuration
    // ==========================================================================

    public static void applyToOkHttp(OkHttpClient.Builder builder) {
        builder.sslSocketFactory(permissiveSocketFactory(), PERMISSIVE_TM)
               .hostnameVerifier(PERMISSIVE_HV);
    }
}
