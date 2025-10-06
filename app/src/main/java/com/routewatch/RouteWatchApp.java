package com.routewatch;

import android.app.Application;
import com.routewatch.util.SharedPreferenceMigrator;

public class RouteWatchApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Migrate old SharedPreferences if needed
        SharedPreferenceMigrator.migrate(this);
    }
}

