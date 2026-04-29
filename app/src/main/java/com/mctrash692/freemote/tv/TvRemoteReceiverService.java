package com.mctrash692.freemote.tv;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.mctrash692.freemote.ui.MouseCursorService;

public class TvRemoteReceiverService extends Service {

    private static final String TAG = "TvRemoteReceiverService";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public void handleCommand(String command) {
        if (command == null) return;

        if (command.startsWith("MOUSE_MOVE")) {
            String[] parts = command.split(" ");
            if (parts.length == 3) {
                try {
                    int deltaX = Integer.parseInt(parts[1]);
                    int deltaY = Integer.parseInt(parts[2]);
                    Intent intent = new Intent(this, MouseCursorService.class);
                    intent.putExtra("action", "MOVE");
                    intent.putExtra("dx", deltaX);
                    intent.putExtra("dy", deltaY);
                    startService(intent);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Bad MOUSE_MOVE args", e);
                }
            }
        } else if (command.equals("MOUSE_CLICK")) {
            Intent intent = new Intent(this, MouseCursorService.class);
            intent.putExtra("action", "CLICK");
            startService(intent);
        } else if (command.equals("CURSOR_SHOW")) {
            Intent intent = new Intent(this, MouseCursorService.class);
            intent.putExtra("action", "SHOW");
            startService(intent);
        } else if (command.equals("CURSOR_HIDE")) {
            Intent intent = new Intent(this, MouseCursorService.class);
            intent.putExtra("action", "HIDE");
            startService(intent);
        } else if (command.startsWith("KEY_")) {
            sendKeyEvent(command.substring(4));
        }
    }

    private void sendKeyEvent(String keyCode) {
        try {
            Runtime.getRuntime().exec("input keyevent KEYCODE_" + keyCode);
        } catch (Exception e) {
            Log.e(TAG, "sendKeyEvent error", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
