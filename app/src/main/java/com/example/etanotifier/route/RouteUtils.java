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
                    int etaMinutes = -1;
                    if (duration != null && duration.contains("m")) {
                        // e.g. "3600s" or "65m"
                        if (duration.endsWith("s")) {
                            try {
                                int seconds = Integer.parseInt(duration.replace("s", ""));
                                etaMinutes = (int) Math.round(seconds / 60.0);
                            } catch (Exception ignore) {}
                        } else if (duration.endsWith("m")) {
                            try {
                                etaMinutes = Integer.parseInt(duration.replace("m", ""));
                            } catch (Exception ignore) {}
                        }
                    } else if (duration != null && duration.endsWith("s")) {
                        try {
                            int seconds = Integer.parseInt(duration.replace("s", ""));
                            etaMinutes = (int) Math.round(seconds / 60.0);
                        } catch (Exception ignore) {}
                    }
                    double miles = distance >= 0 ? distance / 1609.34 : -1;
                    String milesStr = miles >= 0 ? String.format("%.2f", miles) : "?";
                    String etaStr = etaMinutes >= 0 ? etaMinutes + " min" : duration;
                    message = "ETA: " + etaStr + ", Distance: " + milesStr + " miles";
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

