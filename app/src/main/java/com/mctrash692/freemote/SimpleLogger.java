package com.mctrash692.freemote;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SimpleLogger {
    private static SimpleLogger instance;
    private PrintWriter writer;
    private final SimpleDateFormat dateFormat =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private SimpleLogger() {
        try {
            File logDir = new File(Environment.getExternalStorageDirectory(), "FreemoteLogs");
            if (!logDir.exists()) logDir.mkdirs();
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            File logFile = new File(logDir, "freemote_crash_" + date + ".txt");
            // N7: open once in append mode; keep writer open for the lifetime of the singleton.
            writer = new PrintWriter(new FileWriter(logFile, true), true /*autoFlush*/);
            write("=== LOG STARTED ===");
        } catch (Exception e) {
            // Can't log if this fails — silently degrade.
        }
    }

    public static synchronized SimpleLogger getInstance() {
        if (instance == null) instance = new SimpleLogger();
        return instance;
    }

    public synchronized void write(String message) {
        try {
            if (writer != null) {
                writer.println(dateFormat.format(new Date()) + " " + message);
            }
            Log.d("SimpleLogger", message);
        } catch (Exception e) {
            // Ignore
        }
    }

    public synchronized void writeError(String message, Throwable t) {
        try {
            if (writer != null) {
                String ts = dateFormat.format(new Date());
                writer.println(ts + " ERROR: " + message);
                if (t != null) writer.println(ts + " Stack: " + Log.getStackTraceString(t));
            }
        } catch (Exception e) {
            // Ignore
        }
    }
}
