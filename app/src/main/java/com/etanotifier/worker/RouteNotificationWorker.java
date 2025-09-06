package com.etanotifier.worker;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.etanotifier.BuildConfig;
import com.etanotifier.model.Route;
import com.etanotifier.route.RouteManager;
import com.etanotifier.receiver.RouteAlarmReceiver;
import com.etanotifier.util.PlacesRouteUtils;
import com.etanotifier.util.WorkManagerHelper;

public class RouteNotificationWorker extends Worker {
    public static final String EXTRA_ROUTE_ID = "route_id";
    public static final String COMMON_TAG = "SCHEDULED_ROUTE_NOTIFICATION"; // Added this line

    public RouteNotificationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String routeId = getInputData().getString(EXTRA_ROUTE_ID);
        if (routeId == null) {
            Log.e(COMMON_TAG, "Route ID is null. Unexpected state in doWork().");
            return Result.failure();
        }
        Route route = RouteManager.getRouteById(getApplicationContext(), routeId);
        if (route == null) {
            Log.e(COMMON_TAG, "Route not found for ID: " + routeId);
            return Result.failure();
        }
        if (!route.isEnabled()) {
            Log.w(COMMON_TAG, "Route is disabled for ID: " + routeId);
            return Result.success();
        }
        try {
            PlacesRouteUtils.fetchRouteEtaAndDistanceWithPlaceIds(
                getApplicationContext(),
                route.getStartPlaceId(),
                route.getEndPlaceId(),
                BuildConfig.GOOGLE_MAPS_API_KEY,
                message -> {
                    try {
                        RouteAlarmReceiver.showNotification(getApplicationContext(), message);
                    } catch (Exception e) {
                        Log.e(COMMON_TAG, "Failed to show notification: " + e.getMessage(), e);
                    }
                }
            );
            WorkManagerHelper.scheduleRouteNotification(getApplicationContext(), route);
        } catch (Exception e) {
            Log.e(COMMON_TAG, "Unexpected error in doWork(): " + e.getMessage(), e);
            return Result.failure();
        }
        return Result.success();
    }
}
