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

        switch (command) {
            case "MOUSE_MOVE":
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
                break;
            case "MOUSE_CLICK":
                Intent intent = new Intent(this, MouseCursorService.class);
                intent.putExtra("action", "CLICK");
                startService(intent);
                break;
            case "CURSOR_SHOW":
                intent = new Intent(this, MouseCursorService.class);
                intent.putExtra("action", "SHOW");
                startService(intent);
                break;
            case "CURSOR_HIDE":
                intent = new Intent(this, MouseCursorService.class);
                intent.putExtra("action", "HIDE");
                startService(intent);
                break;
            default:
                Log.w(TAG, "Unknown command: " + command);
                break;
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
        if (intent != null) {
            String action = intent.getStringExtra("action");
            if (action != null) {
                switch (action) {
                    case "MOVE":
                        int dx = intent.getIntExtra("dx", 0);
                        int dy = intent.getIntExtra("dy", 0);
                        Intent moveIntent = new Intent(this, MouseCursorService.class);
                        moveIntent.putExtra("action", "MOVE");
                        moveIntent.putExtra("dx", dx);
                        moveIntent.putExtra("dy", dy);
                        startService(moveIntent);
                        break;
                    case "CLICK":
                        Intent clickIntent = new Intent(this, MouseCursorService.class);
                        clickIntent.putExtra("action", "CLICK");
                        startService(clickIntent);
                        break;
                }
            }
        }
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
