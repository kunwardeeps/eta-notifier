package com.routewatch.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.util.Map;

public class SharedPreferenceMigrator {

    private static final String TAG = "PrefMigrator";
    private static final String OLD_PACKAGE_NAME = "com.etanotifier";
    private static final String PREFS_NAME = "routes_prefs"; // Unified with RouteStorage
    private static final String MIGRATION_KEY = "migration_complete_from_etanotifier";

    public static void migrate(Context newContext) {
        SharedPreferences newPrefs = newContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Check if we've already migrated
        if (newPrefs.getBoolean(MIGRATION_KEY, false)) {
            Log.i(TAG, "SharedPreferences migration has already been performed. Skipping.");
            return;
        }

        Log.i(TAG, "Starting SharedPreferences migration from " + OLD_PACKAGE_NAME);

        try {
            // Create a context for the old package to access its SharedPreferences
            Context oldContext = newContext.createPackageContext(OLD_PACKAGE_NAME, 0);
            SharedPreferences oldPrefs = oldContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

            Map<String, ?> allOldData = oldPrefs.getAll();

            if (allOldData.isEmpty()) {
                Log.i(TAG, "No data found in old SharedPreferences. Nothing to migrate.");
            } else {
                SharedPreferences.Editor editor = newPrefs.edit();
                for (Map.Entry<String, ?> entry : allOldData.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    // Copy entries to the new preferences
                    if (value instanceof String) {
                        editor.putString(key, (String) value);
                    } else if (value instanceof Integer) {
                        editor.putInt(key, (Integer) value);
                    } else if (value instanceof Long) {
                        editor.putLong(key, (Long) value);
                    } else if (value instanceof Float) {
                        editor.putFloat(key, (Float) value);
                    } else if (value instanceof Boolean) {
                        editor.putBoolean(key, (Boolean) value);
                    }
                    Log.d(TAG, "Migrating key: " + key);
                }
                editor.apply();
                Log.i(TAG, "Successfully migrated " + allOldData.size() + " key-value pairs.");
            }

            // Mark migration as complete in the new preferences
            newPrefs.edit().putBoolean(MIGRATION_KEY, true).apply();

        } catch (PackageManager.NameNotFoundException e) {
            // This will happen if the old version of the app was never installed, which is fine.
            Log.i(TAG, "Old package not found. No migration necessary.");
            // Mark as complete anyway so we don't try again.
            newPrefs.edit().putBoolean(MIGRATION_KEY, true).apply();
        } catch (Exception e) {
            Log.e(TAG, "An error occurred during SharedPreferences migration.", e);
            FirebaseCrashlytics.getInstance().recordException(e);
        }
    }
}
