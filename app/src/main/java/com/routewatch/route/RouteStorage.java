package com.routewatch.route;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.routewatch.model.Route;
import com.routewatch.model.Schedule;
import org.json.JSONObject;
import org.json.JSONException;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

public class RouteStorage {
    private static final String PREFS_NAME = "routes_prefs";

    public static void saveRoute(Context context, Route route) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String json = routeToJson(route);
        if (json == null) {
            Log.e("RouteStorage", "Failed to convert route to JSON for route: " + route.getId());
            FirebaseCrashlytics.getInstance().log("Failed to convert route to JSON for route: " + route.getId());
            return;
        }
        Log.d("RouteStorage", "Saving route: id=" + route.getId() + ", json=" + json);
        editor.putString(route.getId(), json);
        boolean success = editor.commit();
        if (!success) {
            Log.e("RouteStorage", "Failed to save route to SharedPreferences for route: " + route.getId());
            FirebaseCrashlytics.getInstance().log("Failed to save route to SharedPreferences for route: " + route.getId());
        } else {
            Log.d("RouteStorage", "Route saved successfully: id=" + route.getId());
        }
    }

    public static Route getRoute(Context context, String routeId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(routeId, null);
        if (json == null) return null;
        return jsonToRoute(json);
    }

    private static String routeToJson(Route route) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", route.getId());
            obj.put("startLocation", route.getStartLocation());
            obj.put("endLocation", route.getEndLocation());
            obj.put("startPlaceId", route.getStartPlaceId());
            obj.put("endPlaceId", route.getEndPlaceId());
            if (route.getSchedule() != null) {
                JSONObject sched = new JSONObject();
                sched.put("hour", route.getSchedule().getHour());
                sched.put("minute", route.getSchedule().getMinute());
                sched.put("intervalMinutes", route.getSchedule().getRepeatIntervalMinutes());
                if (route.getSchedule().getDaysOfWeek() != null) {
                    org.json.JSONArray daysArr = new org.json.JSONArray();
                    for (Integer day : route.getSchedule().getDaysOfWeek()) {
                        daysArr.put(day);
                    }
                    sched.put("daysOfWeek", daysArr);
                }
                obj.put("schedule", sched);
            }
            obj.put("enabled", route.isEnabled());
            return obj.toString();
        } catch (JSONException e) {
            Log.e("RouteStorage", "JSONException in routeToJson: " + e.getMessage(), e);
            FirebaseCrashlytics.getInstance().recordException(e);
            return null;
        }
    }

    private static Route jsonToRoute(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            String id = obj.getString("id");
            String startLocation = obj.getString("startLocation");
            String endLocation = obj.getString("endLocation");
            String startPlaceId = obj.optString("startPlaceId", null);
            String endPlaceId = obj.optString("endPlaceId", null);
            Schedule schedule = null;
            if (obj.has("schedule")) {
                JSONObject sched = obj.getJSONObject("schedule");
                int intervalMinutes = sched.getInt("intervalMinutes");
                int hour = sched.getInt("hour");
                int minute = sched.getInt("minute");
                java.util.Set<Integer> daysOfWeek = null;
                if (sched.has("daysOfWeek")) {
                    daysOfWeek = new java.util.HashSet<>();
                    org.json.JSONArray daysArr = sched.getJSONArray("daysOfWeek");
                    for (int i = 0; i < daysArr.length(); i++) {
                        daysOfWeek.add(daysArr.getInt(i));
                    }
                }
                schedule = new Schedule(hour, minute, intervalMinutes, daysOfWeek);
            }
            boolean enabled = obj.has("enabled") ? obj.getBoolean("enabled") : true;
            return new Route(id, startLocation, endLocation, startPlaceId, endPlaceId, schedule, enabled);
        } catch (Exception e) {
            Log.e("RouteStorage", "Exception in jsonToRoute: " + e.getMessage(), e);
            FirebaseCrashlytics.getInstance().recordException(e);
            return null;
        }
    }

    public static java.util.List<Route> getAllRoutes(Context context) {
        java.util.List<Route> routes = new java.util.ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        java.util.Map<String, ?> allPrefs = prefs.getAll();
        Log.d("RouteStorage", "getAllRoutes: found keys=" + allPrefs.keySet());
        for (String key : allPrefs.keySet()) {
            Object value = allPrefs.get(key);
            if (value instanceof String) {
                String json = (String) value;
                try {
                    Route route = jsonToRoute(json);
                    if (route != null) {
                        routes.add(route);
                        Log.d("RouteStorage", "Loaded route: id=" + route.getId());
                    } else {
                        Log.w("RouteStorage", "Failed to parse route for key=" + key);
                    }
                } catch (Exception e) {
                    Log.e("RouteStorage", "Exception in getAllRoutes for key=" + key + ": " + e.getMessage(), e);
                    FirebaseCrashlytics.getInstance().recordException(e);
                }
            } else {
                Log.w("RouteStorage", "Skipping non-String entry in SharedPreferences: key=" + key + ", type=" + value.getClass().getSimpleName());
            }
        }
        Log.d("RouteStorage", "getAllRoutes: loaded " + routes.size() + " routes");
        return routes;
    }

    public static void deleteRoute(Context context, String routeId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(routeId).apply();
    }
}
