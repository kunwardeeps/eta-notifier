package com.example.etanotifier;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TimePicker;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import com.example.etanotifier.adapter.RouteAdapter;
import com.example.etanotifier.model.Route;
import com.example.etanotifier.model.Schedule;
import com.example.etanotifier.receiver.RouteAlarmReceiver;
import com.example.etanotifier.util.NotificationScheduler;
import com.example.etanotifier.network.GoogleMapsApiService;
import com.example.etanotifier.BuildConfig;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private List<Route> routes = new ArrayList<>();
    private RouteAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ListView lvRoutes = findViewById(R.id.lvRoutes);
        Button btnAddRoute = findViewById(R.id.btnAddRoute);
        adapter = new RouteAdapter(this, routes);
        lvRoutes.setAdapter(adapter);

        btnAddRoute.setOnClickListener(v -> showAddRouteDialog());
    }

    private void showAddRouteDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        EditText etStart = new EditText(this); etStart.setHint("Start location");
        EditText etEnd = new EditText(this); etEnd.setHint("End location");
        Button btnTime = new Button(this); btnTime.setText("Pick time");
        Button btnTest = new Button(this); btnTest.setText("Test");
        final int[] hour = {8};
        final int[] minute = {0};
        btnTime.setOnClickListener(v -> new TimePickerDialog(this, (TimePicker view, int h, int m) -> {
            hour[0] = h; minute[0] = m;
            btnTime.setText(String.format("%02d:%02d", h, m));
        }, hour[0], minute[0], true).show());
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.addView(etStart);
        layout.addView(etEnd);
        layout.addView(btnTime);
        layout.addView(btnTest);
        builder.setView(layout);
        builder.setTitle("Add Route");
        builder.setPositiveButton("Add", (dialog, which) -> {
            String start = etStart.getText().toString();
            String end = etEnd.getText().toString();
            if (start.isEmpty() || end.isEmpty()) {
                Toast.makeText(this, "Please enter both locations", Toast.LENGTH_SHORT).show();
                return;
            }
            Schedule schedule = new Schedule(hour[0], minute[0], 1440); // daily
            Route route = new Route(String.valueOf(System.currentTimeMillis()), start, end, schedule);
            routes.add(route);
            adapter.notifyDataSetChanged();
            scheduleRouteNotification(route);
        });
        builder.setNegativeButton("Cancel", null);
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        btnTest.setOnClickListener(v -> {
            String start = etStart.getText().toString();
            String end = etEnd.getText().toString();
            if (start.isEmpty() || end.isEmpty()) {
                Toast.makeText(this, "Please enter both locations", Toast.LENGTH_SHORT).show();
                return;
            }
            // For demo: treat start/end as comma-separated lat,lng (e.g. "37.420761,-122.081356")
            try {
                String[] startParts = start.split(",");
                String[] endParts = end.split(",");
                double startLat = Double.parseDouble(startParts[0].trim());
                double startLng = Double.parseDouble(startParts[1].trim());
                double endLat = Double.parseDouble(endParts[0].trim());
                double endLng = Double.parseDouble(endParts[1].trim());
                String requestBody = "{\"origins\":[{\"waypoint\":{\"location\":{\"latLng\":{\"latitude\":" + startLat + ",\"longitude\":" + startLng + "}}},\"routeModifiers\":{\"avoid_ferries\":true}}],\"destinations\":[{\"waypoint\":{\"location\":{\"latLng\":{\"latitude\":" + endLat + ",\"longitude\":" + endLng + "}}}}],\"travelMode\":\"DRIVE\",\"routingPreference\":\"TRAFFIC_AWARE\"}";
                String fieldMask = "originIndex,destinationIndex,duration,distanceMeters,status,condition";
                new Thread(() -> {
                    String apiKey = BuildConfig.GOOGLE_MAPS_API_KEY;
                    GoogleMapsApiService apiService = new GoogleMapsApiService(apiKey);
                    String message;
                    try {
                        org.json.JSONArray result = apiService.computeRouteMatrix(requestBody, fieldMask);
                        if (result != null && result.length() > 0) {
                            org.json.JSONObject obj = result.getJSONObject(0);
                            String duration = obj.optString("duration", "?");
                            int distance = obj.optInt("distanceMeters", -1);
                            message = "ETA: " + duration + ", Distance: " + (distance >= 0 ? distance + "m" : "?");
                        } else {
                            message = "No route found or empty response.";
                        }
                    } catch (Exception ex) {
                        message = "API call failed: " + ex.getMessage();
                    }
                    String finalMessage = message;
                    runOnUiThread(() -> showNotification(finalMessage));
                }).start();
            } catch (Exception parseEx) {
                showNotification("Invalid input. Use: lat,lng");
            }
        });
    }

    private void scheduleRouteNotification(Route route) {
        Calendar cal = route.getSchedule().getNextScheduledTime();
        Intent intent = new Intent(this, RouteAlarmReceiver.class);
        intent.putExtra("start_location", route.getStartLocation());
        intent.putExtra("end_location", route.getEndLocation());
        intent.putExtra("route_id", route.getId());
        NotificationScheduler.scheduleNotification(this, cal.getTimeInMillis(), intent, route.getId().hashCode());
        Toast.makeText(this, "Notification scheduled", Toast.LENGTH_SHORT).show();
    }

    private void showNotification(String message) {
        String channelId = "test_route_channel";
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Test Route", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Route Test Result")
                .setContentText(message)
                .setAutoCancel(true);
        notificationManager.notify(1001, builder.build());
    }
}
