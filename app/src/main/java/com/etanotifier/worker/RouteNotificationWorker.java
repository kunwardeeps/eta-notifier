package com.etanotifier.worker;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.etanotifier.BuildConfig;
import com.etanotifier.model.Route;
import com.etanotifier.route.RouteManager;
import com.etanotifier.receiver.RouteAlarmReceiver;
import com.etanotifier.util.PlacesRouteUtils;

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
                PlacesRouteUtils.fetchRouteEtaAndDistanceWithPlaceIds(
                    getApplicationContext(),
                    route.getStartPlaceId(),
                    route.getEndPlaceId(),
                    BuildConfig.GOOGLE_MAPS_API_KEY,
                    message -> RouteAlarmReceiver.showNotification(getApplicationContext(), message)
                );
            }
        }
        return Result.success();
    }
}
