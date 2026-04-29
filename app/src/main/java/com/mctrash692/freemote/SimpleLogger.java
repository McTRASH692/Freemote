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
    private File logFile;
    private SimpleDateFormat dateFormat;
    
    private SimpleLogger() {
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        try {
            File logDir = new File(Environment.getExternalStorageDirectory(), "FreemoteLogs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            logFile = new File(logDir, "freemote_crash_" + date + ".txt");
            write("=== LOG STARTED ===");
        } catch (Exception e) {
            // Can't log if this fails
        }
    }
    
    public static synchronized SimpleLogger getInstance() {
        if (instance == null) {
            instance = new SimpleLogger();
        }
        return instance;
    }
    
    public void write(String message) {
        try {
            if (logFile != null) {
                String timestamp = dateFormat.format(new Date());
                String line = timestamp + " " + message + "\n";
                FileWriter fw = new FileWriter(logFile, true);
                fw.write(line);
                fw.close();
                Log.d("SimpleLogger", message);
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    public void writeError(String message, Throwable t) {
        try {
            if (logFile != null) {
                String timestamp = dateFormat.format(new Date());
                FileWriter fw = new FileWriter(logFile, true);
                fw.write(timestamp + " ERROR: " + message + "\n");
                if (t != null) {
                    fw.write(timestamp + " Stack trace: " + Log.getStackTraceString(t) + "\n");
                }
                fw.close();
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    public String getLogPath() {
        return logFile != null ? logFile.getAbsolutePath() : "No log file";
    }
}
