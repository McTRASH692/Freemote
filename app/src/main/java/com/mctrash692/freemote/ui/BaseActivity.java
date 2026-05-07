package com.mctrash692.freemote.ui;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.mctrash692.freemote.R;
import com.mctrash692.freemote.util.CustomThemeManager;
import com.mctrash692.freemote.util.ThemeManager;

public abstract class BaseActivity extends AppCompatActivity {

    private boolean isCustomTheme = false;
    private int bgColor, buttonBg, primary, textPrimary, textSecondary;
    private int navZone, divider, touchpadBg, touchpadText;
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme first
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);

        // Check for custom theme after theme is applied
        isCustomTheme = CustomThemeManager.isCustomThemeActive(this);
        if (isCustomTheme) {
            bgColor = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_BACKGROUND);
            buttonBg = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_BUTTON_BG);
            primary = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_PRIMARY);
            textPrimary = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_TEXT_PRIMARY);
            textSecondary = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_TEXT_SECONDARY);
            navZone = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_NAV_ZONE);
            divider = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_DIVIDER);
            touchpadBg = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_TOUCHPAD_BG);
            touchpadText = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_TOUCHPAD_TEXT);
        }

        density = getResources().getDisplayMetrics().density;
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        if (isCustomTheme) applyCustomColors(getWindow().getDecorView());
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        if (isCustomTheme) applyCustomColors(getWindow().getDecorView());
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        if (isCustomTheme) applyCustomColors(getWindow().getDecorView());
    }

    private GradientDrawable makeBackground(int color, int cornerRadius, int strokeColor, float strokeWidth) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(color);
        gd.setCornerRadius(cornerRadius * density);
        gd.setStroke(Math.round(strokeWidth * density), strokeColor);
        return gd;
    }

    private void applyCustomColors(View view) {
        if (view == null) return;

        if (view instanceof ImageButton) {
            ImageButton btn = (ImageButton) view;
            Object tag = btn.getTag();
            boolean isBrandIcon = "brand_icon".equals(tag);

            btn.setBackground(makeBackground(buttonBg, 8, primary, 2));

            if (!isBrandIcon) {
                btn.setColorFilter(textPrimary);
            } else {
                btn.clearColorFilter();
            }
        } else if (view instanceof Button) {
            ((Button) view).setBackground(makeBackground(buttonBg, 8, primary, 2));
            ((Button) view).setTextColor(textPrimary);
        } else if (view instanceof EditText) {
            GradientDrawable gd = makeBackground(buttonBg, 4, buttonBg, 0);
            view.setBackground(gd);
            ((EditText) view).setTextColor(textPrimary);
            ((EditText) view).setHintTextColor(textSecondary);
        } else if (view instanceof Spinner) {
            GradientDrawable gd = makeBackground(buttonBg, 8, buttonBg, 0);
            view.setBackground(gd);
        } else if (view instanceof TextView) {
            TextView tv = (TextView) view;
            String text = tv.getText() != null ? tv.getText().toString() : "";
            if (text.equals("VOL") || text.equals("CH") || text.equals("VOL") || 
                tv.getHint() != null || (tv.getTag() != null && "brand_label".equals(tv.getTag()))) {
                tv.setTextColor(textSecondary);
            } else {
                tv.setTextColor(textPrimary);
            }
        } else if (view instanceof Switch) {
            // Keep switch default styling
        }

        if (view.getId() == R.id.layoutTouchpad) {
            view.setBackground(makeBackground(touchpadBg, 16, primary, 2));
            if (view instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) view;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View child = vg.getChildAt(i);
                    if (child instanceof TextView) {
                        ((TextView) child).setTextColor(touchpadText);
                    }
                }
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyCustomColors(group.getChildAt(i));
            }
        }
    }
}
