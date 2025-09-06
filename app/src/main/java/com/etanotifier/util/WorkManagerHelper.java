package com.etanotifier.util;

import android.content.Context;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import com.etanotifier.model.Route;
import com.etanotifier.worker.RouteNotificationWorker;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import android.util.Log;

public class WorkManagerHelper {
    public static void scheduleRouteNotification(Context context, Route route) {
        if (!route.isEnabled() || route.getSchedule() == null) {
            Log.w("WorkManagerHelper", "Route is disabled or has no schedule. Cancelling notification for route: " + route.getId());
            cancelRouteNotification(context, route); // Cancel if disabled or no schedule
            return;
        }

        Calendar scheduledTime = route.getSchedule().getNextScheduledTime();
        if (scheduledTime == null) {
            Log.e("WorkManagerHelper", "No valid future time to schedule for route: " + route.getId());
            return; // No valid future time to schedule
        }

        long delayInMillis = scheduledTime.getTimeInMillis() - System.currentTimeMillis();
        if (delayInMillis < 0) {
            Log.w("WorkManagerHelper", "Negative delay for route: " + route.getId() + ". Setting delay to 0.");
            delayInMillis = 0; // Should not happen with getNextScheduledTime, but as a safeguard
        }

        try {
            Data inputData = new Data.Builder()
                    .putString(RouteNotificationWorker.EXTRA_ROUTE_ID, route.getId())
                    .build();

            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            OneTimeWorkRequest notificationWork = new OneTimeWorkRequest.Builder(RouteNotificationWorker.class)
                    .setInitialDelay(delayInMillis, TimeUnit.MILLISECONDS)
                    .setInputData(inputData)
                    .setConstraints(constraints)
                    .addTag("route_notification_" + route.getId()) // Existing unique tag
                    .addTag(RouteNotificationWorker.COMMON_TAG) // New common tag
                    .build();

            WorkManager.getInstance(context)
                    .beginUniqueWork(
                            "route_notification_" + route.getId(),
                            ExistingWorkPolicy.REPLACE,
                            notificationWork
                    )
                    .enqueue();
        } catch (Exception e) {
            Log.e("WorkManagerHelper", "Failed to schedule route notification: " + e.getMessage(), e);
        }
    }

    public static void cancelRouteNotification(Context context, Route route) {
        try {
            WorkManager.getInstance(context)
                    .cancelUniqueWork("route_notification_" + route.getId());
        } catch (Exception e) {
            Log.e("WorkManagerHelper", "Failed to cancel route notification: " + e.getMessage(), e);
        }
    }
}
