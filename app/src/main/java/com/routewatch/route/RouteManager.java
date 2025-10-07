package com.routewatch.route;

import android.content.Context;
import android.util.Log;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.routewatch.model.Route;
import java.util.List;

public class RouteManager {
    public static List<Route> getAllRoutes(Context context) {
        List<Route> routes = null;
        try {
            routes = RouteStorage.getAllRoutes(context);
        } catch (Exception e) {
            Log.e("RouteManager", "Failed to get all routes: " + e.getMessage(), e);
            FirebaseCrashlytics.getInstance().recordException(e);
        }
        if (routes == null) {
            Log.e("RouteManager", "getAllRoutes returned null");
            routes = new java.util.ArrayList<>();
        }
        return routes;
    }
    public static void saveRoute(Context context, Route route) {
        try {
            RouteStorage.saveRoute(context, route);
        } catch (Exception e) {
            Log.e("RouteManager", "Failed to save route: " + e.getMessage(), e);
            FirebaseCrashlytics.getInstance().recordException(e);
        }
    }
    public static void deleteRoute(Context context, String routeId) {
        try {
            RouteStorage.deleteRoute(context, routeId);
        } catch (Exception e) {
            Log.e("RouteManager", "Failed to delete route: " + e.getMessage(), e);
            FirebaseCrashlytics.getInstance().recordException(e);
        }
    }
    public static Route getRouteById(Context context, String routeId) {
        List<Route> routes = getAllRoutes(context);
        for (Route route : routes) {
            if (route.getId().equals(routeId)) {
                return route;
            }
        }
        Log.w("RouteManager", "Route not found for ID: " + routeId);
        return null;
    }
}
