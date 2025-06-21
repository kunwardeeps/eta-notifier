package com.example.etanotifier.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.example.etanotifier.network.GoogleMapsApiService;

public class RouteAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String startLocation = intent.getStringExtra("start_location");
        String endLocation = intent.getStringExtra("end_location");
        int routeId = intent.getIntExtra("route_id", -1);
        // Call Google Maps API and notify user (to be implemented)
        GoogleMapsApiService.fetchEtaAndDistance(context, startLocation, endLocation, routeId);
    }
}
