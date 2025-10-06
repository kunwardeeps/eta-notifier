package com.routewatch.worker;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.routewatch.BuildConfig;
import com.routewatch.model.Route;
import com.routewatch.route.RouteManager;
import com.routewatch.receiver.RouteAlarmReceiver;
import com.routewatch.util.PlacesRouteUtils;
import com.routewatch.util.WorkManagerHelper;

public class RouteNotificationWorker extends Worker {
    public static final String EXTRA_ROUTE_ID = "route_id";
    public static final String COMMON_TAG = "SCHEDULED_ROUTE_NOTIFICATION";
    private FirebaseAnalytics mFirebaseAnalytics;

    public RouteNotificationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        String routeId = getInputData().getString(EXTRA_ROUTE_ID);
        try {
            if (routeId == null) {
                throw new IllegalArgumentException("Route ID is null. Cannot perform work.");
            }
            Route route = RouteManager.getRouteById(getApplicationContext(), routeId);
            if (route == null) {
                Log.e(COMMON_TAG, "Route not found for ID: " + routeId + ". Discontinuing work for this route.");
                // Returning success to stop WorkManager from retrying a non-existent route
                return Result.success();
            }
            if (!route.isEnabled()) {
                Log.w(COMMON_TAG, "Route is disabled. Skipping notification for ID: " + routeId);
                return Result.success();
            }

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
                        Bundle params = new Bundle();
                        params.putString("exception_type", "notification_display_error");
                        params.putString("error_message", e.getMessage());
                        params.putString("route_id", routeId);
                        mFirebaseAnalytics.logEvent("worker_exception", params);
                    }
                }
            );
            WorkManagerHelper.scheduleRouteNotification(getApplicationContext(), route);
            return Result.success();
        } catch (Exception e) {
            Log.e(COMMON_TAG, "Fatal error in doWork(): " + e.getMessage(), e);
            Bundle params = new Bundle();
            params.putString("exception_type", "worker_execution_error");
            params.putString("error_message", e.getMessage());
            if (routeId != null) {
                params.putString("route_id", routeId);
            }
            mFirebaseAnalytics.logEvent("worker_exception", params);
            return Result.failure();
        }
    }
}
