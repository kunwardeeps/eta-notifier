package com.example.etanotifier;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TimePicker;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.example.etanotifier.adapter.RouteAdapter;
import com.example.etanotifier.model.Route;
import com.example.etanotifier.model.Schedule;
import com.example.etanotifier.receiver.RouteAlarmReceiver;
import com.example.etanotifier.util.NotificationScheduler;
import com.example.etanotifier.network.GoogleMapsApiService;
import com.example.etanotifier.BuildConfig;
import com.example.etanotifier.util.RouteUtils;
import com.example.etanotifier.util.RouteStorage;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FetchPlaceResponse;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import android.widget.ArrayAdapter;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.AutoCompleteTextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private List<Route> routes = new ArrayList<>();
    private RouteAdapter adapter;
    private PlacesClient placesClient;
    private AutocompleteSessionToken sessionToken;
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ListView lvRoutes = findViewById(R.id.lvRoutes);
        Button btnAddRoute = findViewById(R.id.btnAddRoute);
        adapter = new RouteAdapter(this, routes);
        lvRoutes.setAdapter(adapter);

        // Initialize Places API
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), BuildConfig.GOOGLE_MAPS_API_KEY);
        }
        placesClient = Places.createClient(this);
        sessionToken = AutocompleteSessionToken.newInstance();

        checkAndRequestNotificationPermission();

        btnAddRoute.setOnClickListener(v -> showAddRouteDialog());
    }

    private void checkAndRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_POST_NOTIFICATIONS);
            }
        }
    }

    private void showAddRouteDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        AutoCompleteTextView etStart = new AutoCompleteTextView(this); etStart.setHint("Start location");
        AutoCompleteTextView etEnd = new AutoCompleteTextView(this); etEnd.setHint("End location");
        // Maps to store suggestion text to placeId for each field
        java.util.Map<String, String> startSuggestionPlaceIdMap = new java.util.HashMap<>();
        java.util.Map<String, String> endSuggestionPlaceIdMap = new java.util.HashMap<>();
        setupAutocomplete(etStart, startSuggestionPlaceIdMap);
        setupAutocomplete(etEnd, endSuggestionPlaceIdMap);
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
            String startPlaceId = startSuggestionPlaceIdMap.get(start);
            String endPlaceId = endSuggestionPlaceIdMap.get(end);
            if (start.isEmpty() || end.isEmpty()) {
                Toast.makeText(this, "Please enter both locations", Toast.LENGTH_SHORT).show();
                return;
            }
            if (startPlaceId == null || endPlaceId == null) {
                Toast.makeText(this, "Please select a valid address from suggestions", Toast.LENGTH_SHORT).show();
                return;
            }
            Schedule schedule = new Schedule(hour[0], minute[0], 1440); // daily
            Route route = new Route(String.valueOf(System.currentTimeMillis()), start, end, startPlaceId, endPlaceId, schedule);
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
            String startPlaceId = startSuggestionPlaceIdMap.get(start);
            String endPlaceId = endSuggestionPlaceIdMap.get(end);
            Route route = new Route("test", start, end, startPlaceId, endPlaceId, null);
            testRouteAndNotify(route);
        });
    }

    // Overloaded setupAutocomplete to accept a placeId map
    private void setupAutocomplete(AutoCompleteTextView autoCompleteTextView, java.util.Map<String, String> suggestionPlaceIdMap) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line);
        autoCompleteTextView.setAdapter(adapter);
        autoCompleteTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() < 5) return;
                FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                        .setQuery(s.toString())
                        .setSessionToken(sessionToken)
                        .build();
                placesClient.findAutocompletePredictions(request)
                        .addOnSuccessListener(response -> {
                            adapter.clear();
                            suggestionPlaceIdMap.clear();
                            for (AutocompletePrediction prediction : response.getAutocompletePredictions()) {
                                String text = prediction.getFullText(null).toString();
                                String placeId = prediction.getPlaceId();
                                adapter.add(text);
                                suggestionPlaceIdMap.put(text, placeId);
                            }
                            adapter.notifyDataSetChanged();
                        })
                        .addOnFailureListener(exception -> {
                            Toast.makeText(MainActivity.this, "Autocomplete failed: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                        });
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void scheduleRouteNotification(Route route) {
        Calendar cal = route.getSchedule().getNextScheduledTime();
        Intent intent = new Intent(this, RouteAlarmReceiver.class);
        intent.putExtra("route_id", route.getId());
        RouteStorage.saveRoute(this, route);
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

    // Modular method to test a route and show notification
    private void testRouteAndNotify(Route route) {
        if (route.getStartLocation().isEmpty() || route.getEndLocation().isEmpty()) {
            Toast.makeText(this, "Please enter both locations", Toast.LENGTH_SHORT).show();
            return;
        }
        if (route.getStartPlaceId() == null || route.getEndPlaceId() == null) {
            Toast.makeText(this, "Please select a valid address from suggestions", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Place.Field> placeFields = Arrays.asList(Place.Field.LOCATION);
        Task<FetchPlaceResponse> startTask = placesClient.fetchPlace(
                FetchPlaceRequest.newInstance(route.getStartPlaceId(), placeFields)
        );
        Task<FetchPlaceResponse> endTask = placesClient.fetchPlace(
                FetchPlaceRequest.newInstance(route.getEndPlaceId(), placeFields)
        );
        startTask.addOnSuccessListener(startResponse -> {
            Place startPlace = startResponse.getPlace();
            LatLng startLatLng = startPlace.getLocation();
            if (startLatLng == null) {
                showNotification("Could not resolve coordinates for start address.");
                return;
            }
            endTask.addOnSuccessListener(endResponse -> {
                Place endPlace = endResponse.getPlace();
                LatLng endLatLng = endPlace.getLocation();
                if (endLatLng == null) {
                    showNotification("Could not resolve coordinates for end address.");
                    return;
                }
                String apiKey = BuildConfig.GOOGLE_MAPS_API_KEY;
                RouteUtils.fetchRouteEtaAndDistance(
                    this,
                    String.valueOf(startLatLng.latitude),
                    String.valueOf(startLatLng.longitude),
                    String.valueOf(endLatLng.latitude),
                    String.valueOf(endLatLng.longitude),
                    apiKey,
                    this::showNotification
                );
            }).addOnFailureListener(e -> showNotification("Failed to resolve end address: " + e.getMessage()));
        }).addOnFailureListener(e -> showNotification("Failed to resolve start address: " + e.getMessage()));
    }
}
