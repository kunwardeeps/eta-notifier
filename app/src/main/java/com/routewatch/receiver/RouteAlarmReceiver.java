package com.routewatch.receiver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.routewatch.R;
import com.routewatch.model.Route;
import com.routewatch.route.RouteManager;
import com.routewatch.worker.RouteNotificationWorker;

public class RouteAlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "route_notifications";
    private static final int NOTIFICATION_ID = 1;
    private static final String EXTRA_ROUTE_ID = "route_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        String routeId = intent.getStringExtra(EXTRA_ROUTE_ID);
        if (routeId == null) {
            Log.e("RouteAlarmReceiver", "Received null routeId in onReceive");
            return;
        }
        Route route = RouteManager.getRouteById(context, routeId);
        if (route == null) {
            Log.e("RouteAlarmReceiver", "Route not found for ID: " + routeId);
            return;
        }
        if (!route.isEnabled()) {
            Log.w("RouteAlarmReceiver", "Route is disabled for ID: " + routeId);
            return;
        }
        try {
            showNotification(context, "Time to check your route to " + route.getEndLocation());
        } catch (Exception e) {
            Log.e("RouteAlarmReceiver", "Failed to show notification: " + e.getMessage(), e);
            FirebaseCrashlytics.getInstance().recordException(e);
        }

        // Schedule the next notification using WorkManager
        if (route.getSchedule() != null && route.getSchedule().getDaysOfWeek() != null
            && !route.getSchedule().getDaysOfWeek().isEmpty()) {
            Data inputData = new Data.Builder()
                .putString(RouteNotificationWorker.EXTRA_ROUTE_ID, routeId)
                .build();
            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(RouteNotificationWorker.class)
                .setInputData(inputData)
                // TODO: Set delay based on route schedule
                .build();
            WorkManager.getInstance(context).enqueue(workRequest);
        }
    }

    private static void createNotificationChannel(Context context) {
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

    public static void showNotification(Context context, String message) {
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
}
