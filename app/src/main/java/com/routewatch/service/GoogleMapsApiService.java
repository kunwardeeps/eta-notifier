package com.routewatch.service;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class GoogleMapsApiService {
    private static final String TAG = "GoogleMapsApiService";
    private static final String API_URL = "https://maps.googleapis.com/maps/api/directions/json";
    private static final String DISTANCE_MATRIX_URL = "https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix";
    private String apiKey;

    public GoogleMapsApiService(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Calls the Google Routes API Distance Matrix endpoint with the given JSON body and returns the response as a JSONArray.
     * @param requestBody The JSON request body as a String.
     * @param fieldMask The X-Goog-FieldMask header value (e.g., "originIndex,destinationIndex,duration,distanceMeters,status,condition")
     * @return JSONArray response or null on error
     */
    public JSONArray computeRouteMatrix(String requestBody, String fieldMask) {
        try {
            URL url = new URL(DISTANCE_MATRIX_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Goog-Api-Key", apiKey);
            conn.setRequestProperty("X-Goog-FieldMask", fieldMask);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            int code = conn.getResponseCode();
            BufferedReader in = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream()
            ));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            return new JSONArray(response.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error calling computeRouteMatrix", e);
            return null;
        }
    }
}
