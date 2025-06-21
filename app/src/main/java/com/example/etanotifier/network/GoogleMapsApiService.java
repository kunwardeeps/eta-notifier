package com.example.etanotifier.network;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class GoogleMapsApiService {
    private static final String TAG = "GoogleMapsApiService";
    private static final String API_URL = "https://maps.googleapis.com/maps/api/directions/json";
    private String apiKey;

    public GoogleMapsApiService(String apiKey) {
        this.apiKey = apiKey;
    }

    public JSONObject getRouteInfo(String origin, String destination) {
        try {
            String urlString = API_URL + "?origin=" + origin + "&destination=" + destination + "&key=" + apiKey;
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            return new JSONObject(response.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error fetching route info", e);
            return null;
        }
    }
}
