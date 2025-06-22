package com.example.etanotifier.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.etanotifier.model.Route;
import com.example.etanotifier.model.Schedule;
import org.json.JSONObject;
import org.json.JSONException;

public class RouteStorage {
    private static final String PREFS_NAME = "routes_prefs";

    public static void saveRoute(Context context, Route route) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(route.getId(), routeToJson(route));
        editor.apply();
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
                obj.put("schedule", sched);
            }
            return obj.toString();
        } catch (JSONException e) {
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
                schedule = new Schedule(
                    sched.getInt("hour"),
                    sched.getInt("minute"),
                    intervalMinutes
                );
            }
            return new Route(id, startLocation, endLocation, startPlaceId, endPlaceId, schedule);
        } catch (JSONException e) {
            return null;
        }
    }
}
