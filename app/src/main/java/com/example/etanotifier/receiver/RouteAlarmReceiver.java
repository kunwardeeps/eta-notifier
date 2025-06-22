package com.example.etanotifier.receiver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.example.etanotifier.BuildConfig;
import com.example.etanotifier.model.Route;
import com.example.etanotifier.util.RouteStorage;
import com.example.etanotifier.util.RouteUtils;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FetchPlaceResponse;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.gms.maps.model.LatLng;
import java.util.Arrays;
import java.util.List;

public class RouteAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String routeId = intent.getStringExtra("route_id");
        if (routeId == null) {
            showNotification(context, "No route ID provided");
            return;
        }
        Route route = RouteStorage.getRoute(context, routeId);
        if (route == null) {
            showNotification(context, "Route not found");
            return;
        }
        if (route.getStartPlaceId() == null || route.getEndPlaceId() == null) {
            showNotification(context, "Route missing place IDs");
            return;
        }
        // Fetch coordinates for place IDs before calling RouteUtils
        PlacesClient placesClient = Places.createClient(context);
        List<Place.Field> placeFields = Arrays.asList(Place.Field.LOCATION);
        Task<FetchPlaceResponse> startTask = placesClient.fetchPlace(
            FetchPlaceRequest.newInstance(route.getStartPlaceId(), placeFields)
        );
        Task<FetchPlaceResponse> endTask = placesClient.fetchPlace(
            FetchPlaceRequest.newInstance(route.getEndPlaceId(), placeFields)
        );
        startTask.addOnSuccessListener(startResponse -> {
            Place startPlace = startResponse.getPlace();
            LatLng startLatLng = startPlace.getLocation();
            if (startLatLng == null) {
                showNotification(context, "Could not resolve coordinates for start address.");
                return;
            }
            endTask.addOnSuccessListener(endResponse -> {
                Place endPlace = endResponse.getPlace();
                LatLng endLatLng = endPlace.getLocation();
                if (endLatLng == null) {
                    showNotification(context, "Could not resolve coordinates for end address.");
                    return;
                }
                String apiKey = BuildConfig.GOOGLE_MAPS_API_KEY;
                RouteUtils.fetchRouteEtaAndDistance(
                    context,
                    String.valueOf(startLatLng.latitude),
                    String.valueOf(startLatLng.longitude),
                    String.valueOf(endLatLng.latitude),
                    String.valueOf(endLatLng.longitude),
                    apiKey,
                    message -> showNotification(context, message)
                );
            }).addOnFailureListener(e -> showNotification(context, "Failed to resolve end address: " + e.getMessage()));
        }).addOnFailureListener(e -> showNotification(context, "Failed to resolve start address: " + e.getMessage()));
    }

    private void showNotification(Context context, String message) {
        String channelId = "route_alarm_channel";
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Route Alarm", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Route Notification")
                .setContentText(message)
                .setAutoCancel(true);
        notificationManager.notify(2001, builder.build());
    }
}
