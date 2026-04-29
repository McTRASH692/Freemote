package com.mctrash692.freemote.ui;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.mctrash692.freemote.R;

public class MouseCursorService extends Service {

    private static final String TAG = "MouseCursorService";

    private WindowManager windowManager;
    private ImageView cursorView;
    private WindowManager.LayoutParams cursorParams;
    private int screenWidth;
    private int screenHeight;
    private int cursorX = 0;
    private int cursorY = 0;
    private boolean isVisible = false;

    private final Handler hideHandler = new Handler();
    private final Runnable hideRunnable = () -> {
        if (cursorView != null) {
            cursorView.setVisibility(View.INVISIBLE);
            isVisible = false;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                Log.e(TAG, "No overlay permission — stopping");
                stopSelf();
                return;
            }
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        android.graphics.Point size = new android.graphics.Point();
        windowManager.getDefaultDisplay().getSize(size);
        screenWidth = size.x;
        screenHeight = size.y;

        cursorX = screenWidth / 2;
        cursorY = screenHeight / 2;

        cursorView = new ImageView(this);
        cursorView.setImageResource(R.drawable.ic_mouse_cursor);
        cursorView.setVisibility(View.INVISIBLE);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        cursorParams = new WindowManager.LayoutParams(
            64, 64,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );

        cursorParams.gravity = Gravity.TOP | Gravity.START;
        cursorParams.x = cursorX - 32;
        cursorParams.y = cursorY - 32;

        windowManager.addView(cursorView, cursorParams);
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
                        moveCursor(dx, dy);
                        break;
                    case "CLICK":
                        clickAtCursor();
                        break;
                    case "SHOW":
                        showCursor();
                        break;
                    case "HIDE":
                        hideCursor();
                        break;
                }
            }
        }
        return START_STICKY;
    }

    public void showCursor() {
        if (cursorView != null && !isVisible) {
            cursorView.setVisibility(View.VISIBLE);
            isVisible = true;
        }
        hideHandler.removeCallbacks(hideRunnable);
        hideHandler.postDelayed(hideRunnable, 2000);
    }

    public void hideCursor() {
        if (cursorView != null && isVisible) {
            cursorView.setVisibility(View.INVISIBLE);
            isVisible = false;
        }
        hideHandler.removeCallbacks(hideRunnable);
    }

    public void moveCursor(int deltaX, int deltaY) {
        cursorX = Math.max(0, Math.min(cursorX + deltaX, screenWidth));
        cursorY = Math.max(0, Math.min(cursorY + deltaY, screenHeight));

        cursorParams.x = cursorX - 32;
        cursorParams.y = cursorY - 32;

        if (windowManager != null && cursorView != null) {
            windowManager.updateViewLayout(cursorView, cursorParams);
        }
        showCursor();
    }

    public void clickAtCursor() {
        try {
            Runtime.getRuntime().exec("input tap " + cursorX + " " + cursorY);
        } catch (Exception e) {
            Log.e(TAG, "clickAtCursor error", e);
        }
    }

    @Override
    public void onDestroy() {
        hideHandler.removeCallbacks(hideRunnable);
        if (cursorView != null && windowManager != null) {
            windowManager.removeView(cursorView);
            cursorView = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
