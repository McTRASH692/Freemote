package com.mctrash692.freemote.util;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileLogger {
    private static final String TAG = "FileLogger";
    private static FileLogger instance;
    private Context context;
    private File logFile;
    private SimpleDateFormat dateFormat;

    private FileLogger(Context context) {
        this.context = context.getApplicationContext();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        initLogFile();
    }

    public static synchronized FileLogger getInstance(Context context) {
        if (instance == null) {
            instance = new FileLogger(context);
        }
        return instance;
    }

    private void initLogFile() {
        try {
            File logDir;
            // Try to save to external storage first
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                logDir = new File(Environment.getExternalStorageDirectory(), "FreemoteLogs");
            } else {
                // Fallback to app's private storage
                logDir = new File(context.getFilesDir(), "logs");
            }
            
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            logFile = new File(logDir, "freemote_log_" + date + ".txt");
            
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
            
            Log.d(TAG, "Log file created at: " + logFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to create log file", e);
        }
    }

    public void log(String message) {
        log(Log.INFO, message, null);
    }

    public void log(String tag, String message) {
        log(Log.INFO, tag + ": " + message, null);
    }

    public void logError(String tag, String message, Throwable throwable) {
        log(Log.ERROR, tag + ": " + message, throwable);
    }

    private void log(int level, String message, Throwable throwable) {
        String timestamp = dateFormat.format(new Date());
        String levelStr = level == Log.ERROR ? "ERROR" : "INFO";
        String logLine = timestamp + " [" + levelStr + "] " + message;
        
        // Log to Android logcat
        if (level == Log.ERROR) {
            Log.e(TAG, logLine);
        } else {
            Log.i(TAG, logLine);
        }
        
        // Write to file
        if (logFile != null) {
            try (FileWriter fw = new FileWriter(logFile, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(logLine);
                if (throwable != null) {
                    StringWriter sw = new StringWriter();
                    throwable.printStackTrace(new PrintWriter(sw));
                    pw.println(sw.toString());
                }
                pw.flush();
            } catch (Exception e) {
                Log.e(TAG, "Failed to write to log file", e);
            }
        }
    }

    public String getLogFilePath() {
        return logFile != null ? logFile.getAbsolutePath() : null;
    }
}
