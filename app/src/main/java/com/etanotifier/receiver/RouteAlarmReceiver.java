package com.etanotifier.receiver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.etanotifier.BuildConfig;
import com.etanotifier.R;
import com.etanotifier.model.Route;
import com.etanotifier.route.RouteStorage;
import com.etanotifier.route.RouteUtils;
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
    private static final String CHANNEL_ID = "route_notifications";
    private static final int NOTIFICATION_ID = 1;

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Route Notifications",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for route updates and ETAs");

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void showNotification(Context context, String message) {
        createNotificationChannel(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ETA Update")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true);

        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

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
}
