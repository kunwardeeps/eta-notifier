package com.example.etanotifier.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.example.etanotifier.BuildConfig;
import com.example.etanotifier.network.GoogleMapsApiService;

public class RouteAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String startLocation = intent.getStringExtra("start_location");
        String endLocation = intent.getStringExtra("end_location");
        // Load API key from BuildConfig
        String apiKey = BuildConfig.GOOGLE_MAPS_API_KEY;
        GoogleMapsApiService apiService = new GoogleMapsApiService(apiKey);
        apiService.getRouteInfo(startLocation, endLocation);
        // TODO: Handle the result and notify user
    }
}
