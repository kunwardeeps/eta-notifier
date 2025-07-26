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

public class WorkManagerHelper {
    public static void scheduleRouteNotification(Context context, Route route) {
        if (!route.isEnabled() || route.getSchedule() == null) {
            return;
        }

        Calendar now = Calendar.getInstance();
        Calendar scheduledTime = Calendar.getInstance();
        scheduledTime.set(Calendar.HOUR_OF_DAY, route.getSchedule().getHour());
        scheduledTime.set(Calendar.MINUTE, route.getSchedule().getMinute());
        scheduledTime.set(Calendar.SECOND, 0);

        // If the time has already passed today, schedule for tomorrow
        if (scheduledTime.before(now)) {
            scheduledTime.add(Calendar.DAY_OF_MONTH, 1);
        }

        long delayInMillis = scheduledTime.getTimeInMillis() - now.getTimeInMillis();

        Data inputData = new Data.Builder()
                .putString(RouteNotificationWorker.EXTRA_ROUTE_ID, route.getId())
                .build();

        OneTimeWorkRequest notificationWork = new OneTimeWorkRequest.Builder(RouteNotificationWorker.class)
                .setInitialDelay(delayInMillis, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag("route_notification_" + route.getId())
                .build();

        WorkManager.getInstance(context)
                .beginUniqueWork(
                        "route_notification_" + route.getId(),
                        ExistingWorkPolicy.REPLACE,
                        notificationWork
                )
                .enqueue();
    }

    public static void cancelRouteNotification(Context context, Route route) {
        WorkManager.getInstance(context)
                .cancelUniqueWork("route_notification_" + route.getId());
    }
}
