package com.etanotifier.worker;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.etanotifier.model.Route;
import com.etanotifier.route.RouteManager;
import com.etanotifier.receiver.RouteAlarmReceiver;

public class RouteNotificationWorker extends Worker {
    public static final String EXTRA_ROUTE_ID = "route_id";

    public RouteNotificationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String routeId = getInputData().getString(EXTRA_ROUTE_ID);
        if (routeId != null) {
            Route route = RouteManager.getRouteById(getApplicationContext(), routeId);
            if (route != null && route.isEnabled()) {
                // Directly call notification logic
                RouteAlarmReceiver.showNotification(getApplicationContext(), "Time to check your route to " + route.getEndLocation());
                // Optionally, schedule next notification here using WorkManager
            }
        }
        return Result.success();
    }
}

