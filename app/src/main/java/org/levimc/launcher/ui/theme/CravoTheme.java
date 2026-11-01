package org.levimc.launcher.ui.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/**
 * Central semantic colors for native CRAVO screens and in-game UI.
 * Explicit accent preference overrides the legacy personalization default.
 * UI components must derive accent-tinted visuals here, never hardcode coral.
 */
public final class CravoTheme {
    public static final int DEFAULT_ACCENT = 0xFFD93652;
    public static final int BACKGROUND = 0xFF0C0B0E;
    public static final int SURFACE = 0xFF17151B;
    public static final int TEXT = 0xFFF4F1F5;
    public static final int MUTED = 0xFFB4AAB4;
    private static final String PREFS = "cravo_ui";
    private static final String KEY = "accent";

    private final Context context;
    private final SharedPreferences preferences;

    public CravoTheme(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int accent() {
        return preferences.getInt(KEY, DEFAULT_ACCENT) | 0xFF000000;
    }

    public void setAccent(int color) {
        int opaque = color | 0xFF000000;
        preferences.edit().putInt(KEY, opaque).apply();
        new org.levimc.launcher.util.PersonalizationManager(context).setAccentColor(opaque);
    }

    public int onAccent() {
        // WCAG-oriented contrast; white and other bright accents use dark text.
        return onAccentColor(accent());
    }

    public static int onAccentColor(int color) {
        int dark = 0xFF111116;
        double light = luminance(color);
        double contrastWhite = 1.05 / (light + .05);
        double contrastDark = (Math.max(light, luminance(dark)) + .05)
                / (Math.min(light, luminance(dark)) + .05);
        return contrastDark >= contrastWhite ? dark : Color.WHITE;
    }

    public int accentSoft() {
        return mix(SURFACE, accent(), .23f);
    }

    public int accentOutline() {
        return mix(0xFF49424C, accent(), .46f);
    }

    public int accentHighlight() {
        return mix(accent(), TEXT, .25f);
    }

    public int cardStart() {
        return mix(0xFF28262E, accent(), .12f);
    }

    public GradientDrawable glass(int radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{cardStart(), 0xFF242129, 0xFF16151B});
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(Math.max(1, dp(1)), accentOutline());
        return d;
    }

    public GradientDrawable solid(int color, int radiusDp, int outline) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (outline != 0) d.setStroke(Math.max(1, dp(1)), outline);
        return d;
    }

    public int dp(float amount) {
        return Math.round(amount * context.getResources().getDisplayMetrics().density);
    }

    public static int mix(int base, int tint, float amount) {
        float t = Math.min(1f, Math.max(0f, amount));
        return Color.rgb(
            Math.round(Color.red(base) * (1f - t) + Color.red(tint) * t),
            Math.round(Color.green(base) * (1f - t) + Color.green(tint) * t),
            Math.round(Color.blue(base) * (1f - t) + Color.blue(tint) * t));
    }

    private static double channel(double x) {
        x /= 255.0;
        return x <= .04045 ? x / 12.92 : Math.pow((x + .055) / 1.055, 2.4);
    }

    public static double luminance(int color) {
        return .2126 * channel(Color.red(color))
                + .7152 * channel(Color.green(color))
                + .0722 * channel(Color.blue(color));
    }
}
