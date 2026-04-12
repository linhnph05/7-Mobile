package com.team7.taskflow.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.text.TextUtils;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.team7.taskflow.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Central place to manage project colors.
 * Stores colors by token for DB, and derives UI variants for light/dark mode.
 */
public final class ProjectColorUtils {

    public static final class ProjectColorSpec {
        private final String token;
        private final String displayName;
        private final String hex;

        public ProjectColorSpec(String token, String displayName, String hex) {
            this.token = token;
            this.displayName = displayName;
            this.hex = hex;
        }

        public String getToken() {
            return token;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getHex() {
            return hex;
        }
    }

    private static final List<ProjectColorSpec> PALETTE;
    public static final String DEFAULT_COLOR_TOKEN;

    static {
        List<ProjectColorSpec> colors = new ArrayList<>();
        colors.add(new ProjectColorSpec("ocean_blue", "Ocean Blue", "#4C6FFF"));
        colors.add(new ProjectColorSpec("sunset_orange", "Sunset Orange", "#E68A57"));
        colors.add(new ProjectColorSpec("jade_green", "Jade Green", "#42A986"));
        colors.add(new ProjectColorSpec("violet_mist", "Violet Mist", "#8A6CCF"));
        colors.add(new ProjectColorSpec("rose_pink", "Rose Pink", "#C95A78"));
        PALETTE = Collections.unmodifiableList(colors);
        DEFAULT_COLOR_TOKEN = colors.get(0).getToken();
    }

    private ProjectColorUtils() {
    }

    @NonNull
    public static List<ProjectColorSpec> getPalette() {
        return PALETTE;
    }

    public static int paletteSize() {
        return PALETTE.size();
    }

    public static String getTokenByIndex(int index) {
        if (index < 0 || index >= PALETTE.size()) {
            return DEFAULT_COLOR_TOKEN;
        }
        return PALETTE.get(index).getToken();
    }

    @ColorInt
    public static int getBaseColorByIndex(int index) {
        return parseHexSafe(PALETTE.get(Math.max(0, Math.min(index, PALETTE.size() - 1))).getHex());
    }

    @ColorInt
    public static int resolveBaseColor(@NonNull Context context, String storedValue) {
        if (!TextUtils.isEmpty(storedValue)) {
            String raw = storedValue.trim();

            if (raw.startsWith("#")) {
                return parseHexSafe(raw);
            }

            for (ProjectColorSpec spec : PALETTE) {
                if (spec.getToken().equalsIgnoreCase(raw)) {
                    return parseHexSafe(spec.getHex());
                }
            }

            // Legacy token compatibility after palette update.
            if ("teal_lagoon".equalsIgnoreCase(raw)) {
                return Color.parseColor("#C95A78");
            }
        }
        return parseHexSafe(PALETTE.get(0).getHex());
    }

    @NonNull
    public static String toStorageToken(String candidate) {
        if (TextUtils.isEmpty(candidate)) {
            return DEFAULT_COLOR_TOKEN;
        }
        String normalized = candidate.trim().toLowerCase(Locale.US);
        for (ProjectColorSpec spec : PALETTE) {
            if (spec.getToken().equals(normalized)) {
                return spec.getToken();
            }
        }
        return DEFAULT_COLOR_TOKEN;
    }

    @ColorInt
    public static int resolveChipBackgroundColor(@NonNull Context context, @ColorInt int baseColor) {
        int alpha = isDarkMode(context) ? 66 : 38;
        return ColorUtils.setAlphaComponent(baseColor, alpha);
    }

    @ColorInt
    public static int resolveChipTextColor(@NonNull Context context, @ColorInt int baseColor) {
        return isDarkMode(context)
                ? ColorUtils.blendARGB(baseColor, Color.WHITE, 0.22f)
                : ColorUtils.blendARGB(baseColor, Color.BLACK, 0.18f);
    }

    @ColorInt
    public static int resolveProjectCardBackgroundColor(@NonNull Context context, @ColorInt int baseColor) {
        int card = colorFromRes(context, R.color.theme_card);
        float ratio = isDarkMode(context) ? 0.36f : 0.24f;
        return ColorUtils.blendARGB(card, baseColor, ratio);
    }

    @ColorInt
    public static int resolveProgressTrackColor(@NonNull Context context, @ColorInt int baseColor) {
        int card = resolveProjectCardBackgroundColor(context, baseColor);
        float ratio = isDarkMode(context) ? 0.68f : 0.56f;
        return ColorUtils.blendARGB(card, baseColor, ratio);
    }

    @ColorInt
    public static int resolveProgressFillColor(@NonNull Context context, @ColorInt int baseColor) {
        return isDarkMode(context)
                ? ColorUtils.blendARGB(baseColor, Color.WHITE, 0.30f)
                : ColorUtils.blendARGB(baseColor, Color.BLACK, 0.24f);
    }

    @ColorInt
    public static int resolveHeaderTintColor(@NonNull Context context, @ColorInt int baseColor) {
        int surface = colorFromRes(context, R.color.theme_surface);
        float ratio = isDarkMode(context) ? 0.22f : 0.12f;
        return ColorUtils.blendARGB(surface, baseColor, ratio);
    }

    @ColorInt
    public static int resolveContentTintColor(@NonNull Context context, @ColorInt int baseColor) {
        int background = colorFromRes(context, R.color.theme_background);
        float ratio = isDarkMode(context) ? 0.26f : 0.14f;
        return ColorUtils.blendARGB(background, baseColor, ratio);
    }

    private static int colorFromRes(@NonNull Context context, int colorRes) {
        return androidx.core.content.ContextCompat.getColor(context, colorRes);
    }

    private static boolean isDarkMode(@NonNull Context context) {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    @ColorInt
    private static int parseHexSafe(@NonNull String hex) {
        try {
            return Color.parseColor(hex);
        } catch (IllegalArgumentException ex) {
            return Color.parseColor(PALETTE.get(0).getHex());
        }
    }
}