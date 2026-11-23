package com.example.android.notepad;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Applies the theme chosen by the user (day / night) before any Activity is created.
 */
public class NotePadApp extends Application {

    public static final String PREFS_NAME = "settings";
    public static final String PREF_NIGHT_MODE = "night_mode";

    @Override
    public void onCreate() {
        super.onCreate();
        int mode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(PREF_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
