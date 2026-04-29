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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);

        isCustomTheme = CustomThemeManager.isCustomThemeActive(this);
        if (isCustomTheme) {
            bgColor      = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_BACKGROUND);
            buttonBg     = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_BUTTON_BG);
            primary      = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_PRIMARY);
            textPrimary  = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_TEXT_PRIMARY);
            textSecondary= CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_TEXT_SECONDARY);
            navZone      = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_NAV_ZONE);
            divider      = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_DIVIDER);
            touchpadBg   = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_TOUCHPAD_BG);
            touchpadText = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_TOUCHPAD_TEXT);
        }
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

    /**
     * Build a GradientDrawable that replicates bg_button.xml at runtime:
     *   solid = buttonBg colour, 2dp stroke in primary colour, 8dp corners.
     * This preserves the visual shape that setBackgroundColor() destroys.
     */
    private GradientDrawable makeButtonBackground() {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(buttonBg);
        gd.setCornerRadius(8 * getResources().getDisplayMetrics().density);
        gd.setStroke(Math.round(2 * getResources().getDisplayMetrics().density), primary);
        return gd;
    }

    /**
     * Build a GradientDrawable for the touchpad that replicates bg_touchpad.xml:
     *   solid = touchpadBg, 2dp stroke in primary, 16dp corners.
     */
    private GradientDrawable makeTouchpadBackground() {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(touchpadBg);
        gd.setCornerRadius(16 * getResources().getDisplayMetrics().density);
        gd.setStroke(Math.round(2 * getResources().getDisplayMetrics().density), primary);
        return gd;
    }

    private void applyCustomColors(View view) {
        if (view == null) return;

        if (view instanceof ImageButton) {
            ImageButton btn = (ImageButton) view;
            Object tag = btn.getTag();
            boolean isBrandIcon = "brand_icon".equals(tag);

            // Always restore the button shape with stroke — never use setBackgroundColor.
            btn.setBackground(makeButtonBackground());

            // Tint the icon only for non-brand buttons. Brand/platform icons
            // (YouTube, Netflix, etc.) must keep their original colours.
            if (!isBrandIcon) {
                btn.setColorFilter(textPrimary);
            } else {
                btn.clearColorFilter();
            }
        }
        else if (view instanceof Button) {
            ((Button) view).setBackground(makeButtonBackground());
            ((Button) view).setTextColor(textPrimary);
        }
        else if (view instanceof EditText) {
            // EditText uses a simple solid background — no stroke needed.
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(buttonBg);
            gd.setCornerRadius(4 * getResources().getDisplayMetrics().density);
            view.setBackground(gd);
            ((EditText) view).setTextColor(textPrimary);
            ((EditText) view).setHintTextColor(textSecondary);
        }
        else if (view instanceof Spinner) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(buttonBg);
            view.setBackground(gd);
        }
        else if (view instanceof TextView) {
            // EditText is a subclass of TextView — guard must come after the EditText branch above.
            TextView tv = (TextView) view;
            String text = tv.getText() != null ? tv.getText().toString() : "";
            if (text.equals("VOL") || text.equals("CH") || tv.getHint() != null) {
                tv.setTextColor(textSecondary);
            } else {
                tv.setTextColor(textPrimary);
            }
        }
        // Switch colours come from theme attributes — leave untouched.

        // Touchpad container: use shape drawable with stroke, not setBackgroundColor.
        if (view.getId() == R.id.layoutTouchpad) {
            view.setBackground(makeTouchpadBackground());
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

        // Recurse into children.
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyCustomColors(group.getChildAt(i));
            }
        }
    }
}
