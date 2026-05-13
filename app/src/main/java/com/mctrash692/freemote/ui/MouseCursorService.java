package com.mctrash692.freemote.ui;

// ============================================================================
// FILE: MouseCursorService.java
// WHAT:  A background service that draws a mouse cursor icon on top of
//        everything on your phone screen (a "draw overlay" that floats
//        above other apps). This lets you see where the TV mouse cursor
//        is positioned when using the touchpad. The cursor appears when
//        you move your finger on the touchpad and disappears after 2
//        seconds of not moving. It can also simulate a tap at the cursor
//        position on your phone screen.
// ============================================================================

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.mctrash692.freemote.R;

// ==========================================================================
// SECTION: MOUSE CURSOR SERVICE
// WHAT:  A background service that shows a little mouse cursor icon on
//        your phone screen (floating above everything else). This lets
//        you see where the TV's mouse cursor is when you use the touchpad.
//        The cursor appears when you move your finger and disappears after
//        2 seconds of inactivity.
// ==========================================================================

public class MouseCursorService extends Service {

    private static final String TAG = "MouseCursorService";

    // Manages adding/removing the cursor overlay on the screen
    private WindowManager windowManager;
    // The actual cursor image drawn on screen
    private ImageView cursorView;
    // Position and size settings for the cursor overlay
    private WindowManager.LayoutParams cursorParams;
    // Phone screen dimensions
    private int screenWidth;
    private int screenHeight;
    // Size of the cursor icon in pixels
    private static final int CURSOR_SIZE = 64;
    // Current cursor position on screen
    private int cursorX = 0;
    private int cursorY = 0;
    // Whether the cursor is currently visible
    private boolean isVisible = false;

    // Handler and runnable to hide the cursor after 2 seconds of no movement
    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideRunnable = () -> {
        if (cursorView != null) {
            cursorView.setVisibility(View.INVISIBLE);
            isVisible = false;
        }
    };

    // ==========================================================================
    // METHOD: onCreate
    // WHAT:  Runs when the service starts. Checks that the app has
    //        permission to draw over other apps, then creates the cursor
    //        image and places it in the center of the screen (hidden).
    // ==========================================================================

    @Override
    public void onCreate() {
        super.onCreate();

        // Check overlay permission (required on Android 6+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                Log.e(TAG, "No overlay permission — stopping");
                stopSelf();
                return;
            }
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Get the phone's screen size
        android.graphics.Point size = new android.graphics.Point();
        windowManager.getDefaultDisplay().getSize(size);
        screenWidth = size.x;
        screenHeight = size.y;

        // Start cursor in the center of the screen
        cursorX = screenWidth / 2;
        cursorY = screenHeight / 2;

        // Create the cursor image (starts invisible)
        cursorView = new ImageView(this);
        cursorView.setImageResource(R.drawable.ic_mouse_cursor);
        cursorView.setVisibility(View.INVISIBLE);

        // Choose the right overlay type based on Android version
        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        // Configure how the cursor overlay looks and behaves
        cursorParams = new WindowManager.LayoutParams(
            CURSOR_SIZE, CURSOR_SIZE,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );

        cursorParams.gravity = Gravity.TOP | Gravity.START;
        cursorParams.x = cursorX - CURSOR_SIZE / 2;
        cursorParams.y = cursorY - CURSOR_SIZE / 2;

        // Add the cursor to the screen (it is invisible for now)
        windowManager.addView(cursorView, cursorParams);
    }

    // ==========================================================================
    // METHOD: onStartCommand
    // WHAT:  Runs whenever another part of the app sends a command to this
    //        service. Handles MOVE (move the cursor), CLICK (tap at cursor
    //        position), SHOW (make visible), and HIDE (make invisible).
    // ==========================================================================

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

    // ==========================================================================
    // METHOD: showCursor
    // WHAT:  Makes the cursor visible on screen. Also resets the auto-hide
    //        timer — the cursor will disappear after 2 more seconds if no
    //        new movement happens.
    // ==========================================================================

    public void showCursor() {
        if (cursorView != null && !isVisible) {
            cursorView.setVisibility(View.VISIBLE);
            isVisible = true;
        }
        hideHandler.removeCallbacks(hideRunnable);
        hideHandler.postDelayed(hideRunnable, 2000);
    }

    // ==========================================================================
    // METHOD: hideCursor
    // WHAT:  Makes the cursor invisible on screen immediately and cancels
    //        the auto-hide timer.
    // ==========================================================================

    public void hideCursor() {
        if (cursorView != null && isVisible) {
            cursorView.setVisibility(View.INVISIBLE);
            isVisible = false;
        }
        hideHandler.removeCallbacks(hideRunnable);
    }

    // ==========================================================================
    // METHOD: moveCursor
    // WHAT:  Moves the cursor by the given amount (deltaX, deltaY). Keeps
    //        the cursor within the screen boundaries, updates its position,
    //        and shows it again (resetting the auto-hide timer).
    // INPUT: deltaX = pixels to move right (negative = left)
    //        deltaY = pixels to move down (negative = up)
    // ==========================================================================

    public void moveCursor(int deltaX, int deltaY) {
        if (windowManager == null || cursorView == null) return;

        cursorX = Math.max(0, Math.min(cursorX + deltaX, screenWidth - CURSOR_SIZE));
        cursorY = Math.max(0, Math.min(cursorY + deltaY, screenHeight - CURSOR_SIZE));

        cursorParams.x = cursorX - CURSOR_SIZE / 2;
        cursorParams.y = cursorY - CURSOR_SIZE / 2;

        windowManager.updateViewLayout(cursorView, cursorParams);
        showCursor();
    }

    // ==========================================================================
    // METHOD: clickAtCursor
    // WHAT:  Simulates a finger tap on the phone screen at the cursor's
    //        current position. Used to interact with apps behind the cursor.
    // ==========================================================================

    public void clickAtCursor() {
        try {
            Runtime.getRuntime().exec("input tap " + cursorX + " " + cursorY);
        } catch (Exception e) {
            Log.e(TAG, "clickAtCursor error", e);
        }
    }

    // ==========================================================================
    // METHOD: onDestroy
    // WHAT:  Runs when the service is being shut down. Removes the cursor
    //        overlay from the screen and cancels the auto-hide timer.
    // ==========================================================================

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
