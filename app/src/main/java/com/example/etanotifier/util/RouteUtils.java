package com.example.etanotifier.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.example.etanotifier.network.GoogleMapsApiService;
import org.json.JSONArray;
import org.json.JSONObject;

public class RouteUtils {
    public interface RouteResultCallback {
        void onResult(String message);
    }

    public static void fetchRouteEtaAndDistance(Context context, String startLat, String startLng, String endLat, String endLng, String apiKey, RouteResultCallback callback) {
        String requestBody = "{\"origins\":[{\"waypoint\":{\"location\":{\"latLng\":{\"latitude\":" + startLat + ",\"longitude\":" + startLng + "}}},\"routeModifiers\":{\"avoidTolls\":true}}],\"destinations\":[{\"waypoint\":{\"location\":{\"latLng\":{\"latitude\":" + endLat + ",\"longitude\":" + endLng + "}}}}],\"travelMode\":\"DRIVE\",\"routingPreference\":\"TRAFFIC_AWARE\"}";
        String fieldMask = "originIndex,destinationIndex,duration,distanceMeters,status,condition";
        new Thread(() -> {
            GoogleMapsApiService apiService = new GoogleMapsApiService(apiKey);
            String message;
            try {
                JSONArray result = apiService.computeRouteMatrix(requestBody, fieldMask);
                if (result != null && result.length() > 0) {
                    JSONObject obj = result.getJSONObject(0);
                    String duration = obj.optString("duration", "?");
                    int distance = obj.optInt("distanceMeters", -1);
                    // Convert meters to miles
                    String distanceStr = "?";
                    if (distance >= 0) {
                        double miles = distance / 1609.34;
                        distanceStr = String.format("%.2f mi", miles);
                    }
                    // Convert duration (e.g., "123s") to minutes
                    String etaStr = "?";
                    if (!duration.equals("?")) {
                        try {
                            int seconds = 0;
                            if (duration.endsWith("s")) {
                                seconds = Integer.parseInt(duration.replace("s", ""));
                            } else if (duration.endsWith("m")) {
                                // fallback for minutes, e.g., "5m"
                                seconds = Integer.parseInt(duration.replace("m", "")) * 60;
                            }
                            int minutes = (int) Math.round(seconds / 60.0);
                            etaStr = minutes + " min";
                        } catch (Exception e) {
                            etaStr = duration;
                        }
                    }
                    message = "ETA: " + etaStr + ", Distance: " + distanceStr;
                } else {
                    message = "No route found or empty response.";
                }
            } catch (Exception ex) {
                message = "API call failed: " + ex.getMessage();
            }
            String finalMessage = message;
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(finalMessage));
        }).start();
    }
}
