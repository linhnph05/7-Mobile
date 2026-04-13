package com.team7.taskflow.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.team7.taskflow.R;

import java.util.Locale;

public final class LanguageManager {

    private static final String PREF_NAME = "language_prefs";
    private static final String KEY_LANGUAGE_TAG = "language_tag";

    public static final String LANGUAGE_ENGLISH = "en";
    public static final String LANGUAGE_VIETNAMESE = "vi";

    private LanguageManager() {
    }

    public static void applySavedLanguage(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        String languageTag = prefs.getString(KEY_LANGUAGE_TAG, "");
        if (languageTag == null || languageTag.isEmpty()) {
            languageTag = defaultLanguageTag();
            prefs.edit().putString(KEY_LANGUAGE_TAG, languageTag).apply();
        }

        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag));
    }

    public static void setLanguage(Context context, String languageTag) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE_TAG, languageTag).apply();
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag));
    }

    public static String getCurrentLanguageTag(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String languageTag = prefs.getString(KEY_LANGUAGE_TAG, "");
        if (languageTag != null && !languageTag.isEmpty()) {
            return languageTag;
        }

        Locale currentLocale = Locale.getDefault();
        return LANGUAGE_VIETNAMESE.equals(currentLocale.getLanguage())
                ? LANGUAGE_VIETNAMESE
                : LANGUAGE_ENGLISH;
    }

    public static String getCurrentLanguageLabel(Context context) {
        return LANGUAGE_VIETNAMESE.equals(getCurrentLanguageTag(context))
                ? context.getString(R.string.language_vietnamese)
                : context.getString(R.string.language_english);
    }

    public static int getSelectedIndex(Context context) {
        return LANGUAGE_VIETNAMESE.equals(getCurrentLanguageTag(context)) ? 1 : 0;
    }

    private static String defaultLanguageTag() {
        Locale currentLocale = Locale.getDefault();
        return LANGUAGE_VIETNAMESE.equals(currentLocale.getLanguage())
                ? LANGUAGE_VIETNAMESE
                : LANGUAGE_ENGLISH;
    }
}