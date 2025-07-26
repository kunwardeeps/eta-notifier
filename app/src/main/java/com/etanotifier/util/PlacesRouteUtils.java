package com.etanotifier.util;

import android.content.Context;

import com.etanotifier.service.PlacesHelper;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.Place.Field;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.etanotifier.route.RouteUtils;
import java.util.Arrays;

public class PlacesRouteUtils {
    public interface RouteMessageCallback {
        void onMessage(String message);
    }

    public static void fetchRouteEtaAndDistanceWithPlaceIds(
            Context context,
            String startPlaceId,
            String endPlaceId,
            String apiKey,
            RouteMessageCallback callback
    ) {
        PlacesClient placesClient = PlacesHelper.getPlacesClient(context, apiKey);
        java.util.List<Field> placeFields = Arrays.asList(Field.LOCATION);
        FetchPlaceRequest startRequest = FetchPlaceRequest.newInstance(startPlaceId, placeFields);
        FetchPlaceRequest endRequest = FetchPlaceRequest.newInstance(endPlaceId, placeFields);
        placesClient.fetchPlace(startRequest).addOnSuccessListener(startResponse -> {
            Place startPlace = startResponse.getPlace();
            com.google.android.gms.maps.model.LatLng startLatLng = startPlace.getLocation();
            if (startLatLng == null) {
                callback.onMessage("Could not resolve coordinates for start address.");
                return;
            }
            placesClient.fetchPlace(endRequest).addOnSuccessListener(endResponse -> {
                Place endPlace = endResponse.getPlace();
                com.google.android.gms.maps.model.LatLng endLatLng = endPlace.getLocation();
                if (endLatLng == null) {
                    callback.onMessage("Could not resolve coordinates for end address.");
                    return;
                }
                RouteUtils.fetchRouteEtaAndDistance(
                    context,
                    String.valueOf(startLatLng.latitude),
                    String.valueOf(startLatLng.longitude),
                    String.valueOf(endLatLng.latitude),
                    String.valueOf(endLatLng.longitude),
                    apiKey,
                    callback::onMessage
                );
            }).addOnFailureListener(e -> {
                String errorMsg = "Failed to resolve end address: " + e.getMessage();
                android.util.Log.e("PlacesRouteUtils", errorMsg, e);
                callback.onMessage(errorMsg);
            });
        }).addOnFailureListener(e -> {
            String errorMsg = "Failed to resolve start address: " + e.getMessage();
            android.util.Log.e("PlacesRouteUtils", errorMsg, e);
            callback.onMessage(errorMsg);
        });
    }
}
