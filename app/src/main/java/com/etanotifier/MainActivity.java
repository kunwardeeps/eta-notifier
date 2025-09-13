package com.etanotifier;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TimePicker;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.etanotifier.route.RouteAdapter;
import com.etanotifier.model.Route;
import com.etanotifier.model.Schedule;
import com.etanotifier.service.PlacesHelper;
import com.etanotifier.route.RouteManager;
import com.etanotifier.route.RouteUtils;
import com.etanotifier.util.WorkManagerHelper;
import com.etanotifier.util.PlacesRouteUtils;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.net.PlacesClient;
import android.widget.AutoCompleteTextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.List;
import java.util.Calendar;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.Place.Field;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FetchPlaceResponse;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

public class MainActivity extends AppCompatActivity {
    private List<Route> routes = new ArrayList<>();
    private RouteAdapter adapter;
    private PlacesClient placesClient;
    private AutocompleteSessionToken sessionToken;
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 1001;
    private AdView mAdView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ListView lvRoutes = findViewById(R.id.lvRoutes);
        Button btnAddRoute = findViewById(R.id.btnAddRoute);
        try {
            routes = RouteManager.getAllRoutes(this);
            if (routes == null) {
                Log.e("MainActivity", "Failed to load routes: routes is null");
                routes = new ArrayList<>();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Exception while loading routes: " + e.getMessage(), e);
            routes = new ArrayList<>();
        }
        adapter = new RouteAdapter(this, routes);
        lvRoutes.setAdapter(adapter);
        adapter.setRouteActionListener(new RouteAdapter.RouteActionListener() {
            @Override
            public void onEdit(Route route, int position) {
                showEditRouteDialog(route, position);
            }
            @Override
            public void onDelete(Route route, int position) {
                RouteManager.deleteRoute(MainActivity.this, route.getId());
                routes.remove(position);
                adapter.notifyDataSetChanged();
                WorkManagerHelper.cancelRouteNotification(MainActivity.this, route); // Cancel schedule in WorkManager
                Toast.makeText(MainActivity.this, "Route deleted", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onToggle(Route route, int position, boolean enabled) {
                route.setEnabled(enabled);
                RouteManager.saveRoute(MainActivity.this, route);
                if (enabled) {
                    WorkManagerHelper.scheduleRouteNotification(MainActivity.this, route);
                }
                Toast.makeText(MainActivity.this, enabled ? "Route enabled" : "Route disabled", Toast.LENGTH_SHORT).show();
            }
        });

        placesClient = PlacesHelper.getPlacesClient(this, BuildConfig.GOOGLE_MAPS_API_KEY);
        sessionToken = PlacesHelper.getSessionToken();
        checkAndRequestNotificationPermission();
        btnAddRoute.setOnClickListener(v -> showAddRouteDialog());

        adapter.setOnItemScheduleClickListener((route, position) -> {
            Calendar currentTime = Calendar.getInstance();
            new TimePickerDialog(this,
                (view, hourOfDay, minute) -> {
                    Schedule schedule = new Schedule(hourOfDay, minute, 1440,
                        route.getSchedule() != null ? route.getSchedule().getDaysOfWeek() : new java.util.HashSet<>());
                    route.setSchedule(schedule);
                    adapter.notifyDataSetChanged();
                    RouteManager.saveRoute(this, route);
                    WorkManagerHelper.scheduleRouteNotification(this, route);
                    Toast.makeText(this, "Route schedule updated", Toast.LENGTH_SHORT).show();
                },
                currentTime.get(Calendar.HOUR_OF_DAY),
                currentTime.get(Calendar.MINUTE),
                true
            ).show();
        });

        // Initialize AdMob
        MobileAds.initialize(this, initializationStatus -> {});
        mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
    }

    private void checkAndRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.POST_NOTIFICATIONS)) {
                    // Show an explanation to the user
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Notification Permission Required")
                        .setMessage("RouteWatch needs notification permission to alert you about your routes.")
                        .setPositiveButton("Grant Permission", (dialog, which) -> {
                            ActivityCompat.requestPermissions(this,
                                new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                                REQUEST_CODE_POST_NOTIFICATIONS);
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                            Toast.makeText(this, "Notifications won't work without permission", Toast.LENGTH_LONG).show();
                        })
                        .create()
                        .show();
                } else {
                    ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE_POST_NOTIFICATIONS);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
                // Re-enable any disabled routes that need notifications
                for (Route route : routes) {
                    if (route.isEnabled()) {
                        WorkManagerHelper.scheduleRouteNotification(this, route);
                    }
                }
            } else {
                Toast.makeText(this, "Notifications won't work without permission", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void showAddRouteDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        AutoCompleteTextView etStart = new AutoCompleteTextView(this); etStart.setHint("Start location");
        AutoCompleteTextView etEnd = new AutoCompleteTextView(this); etEnd.setHint("End location");
        Map<String, String> startSuggestionPlaceIdMap = new HashMap<>();
        Map<String, String> endSuggestionPlaceIdMap = new HashMap<>();
        PlacesHelper.setupAutocomplete(this, placesClient, sessionToken, etStart, startSuggestionPlaceIdMap);
        PlacesHelper.setupAutocomplete(this, placesClient, sessionToken, etEnd, endSuggestionPlaceIdMap);

        android.widget.GridLayout daysLayout = new android.widget.GridLayout(this);
        daysLayout.setColumnCount(3);
        String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        int[] dayValues = {Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY};
        android.widget.CheckBox[] dayCheckBoxes = new android.widget.CheckBox[7];
        for (int i = 0; i < 7; i++) {
            dayCheckBoxes[i] = new android.widget.CheckBox(this);
            dayCheckBoxes[i].setText(dayNames[i]);
            daysLayout.addView(dayCheckBoxes[i]);
        }

        Button btnTime = new Button(this); btnTime.setText(getString(R.string.pick_time));
        final int[] hour = {8};
        final int[] minute = {0};
        btnTime.setOnClickListener(v -> new TimePickerDialog(this, (TimePicker view, int h, int m) -> {
            hour[0] = h; minute[0] = m;
            btnTime.setText(String.format(Locale.getDefault(), "%02d:%02d", h, m));
        }, hour[0], minute[0], true).show());

        Button btnTest = new Button(this); btnTest.setText("Test");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.addView(etStart);
        layout.addView(etEnd);
        layout.addView(daysLayout, 2);
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
            Set<Integer> selectedDays = new HashSet<>();
            for (int i = 0; i < 7; i++) {
                if (dayCheckBoxes[i].isChecked()) {
                    selectedDays.add(dayValues[i]);
                }
            }
            Schedule schedule = new Schedule(hour[0], minute[0], 1440, selectedDays);
            Route route = new Route(String.valueOf(System.currentTimeMillis()), start, end, startPlaceId, endPlaceId, schedule);
            routes.add(route);
            adapter.notifyDataSetChanged();
            WorkManagerHelper.scheduleRouteNotification(this, route);
        });
        builder.setNegativeButton("Cancel", null);
        AlertDialog dialog = builder.create();
        dialog.show();
        btnTest.setOnClickListener(v -> {
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
            PlacesRouteUtils.fetchRouteEtaAndDistanceWithPlaceIds(
                this,
                startPlaceId,
                endPlaceId,
                BuildConfig.GOOGLE_MAPS_API_KEY,
                message -> Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            );
        });
    }

    private void showEditRouteDialog(Route route, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        AutoCompleteTextView etStart = new AutoCompleteTextView(this); etStart.setHint("Start location");
        AutoCompleteTextView etEnd = new AutoCompleteTextView(this); etEnd.setHint("End location");
        etStart.setText(route.getStartLocation());
        etEnd.setText(route.getEndLocation());
        Map<String, String> startSuggestionPlaceIdMap = new HashMap<>();
        Map<String, String> endSuggestionPlaceIdMap = new HashMap<>();
        PlacesHelper.setupAutocomplete(this, placesClient, sessionToken, etStart, startSuggestionPlaceIdMap);
        PlacesHelper.setupAutocomplete(this, placesClient, sessionToken, etEnd, endSuggestionPlaceIdMap);

        android.widget.GridLayout daysLayout = new android.widget.GridLayout(this);
        daysLayout.setColumnCount(3);
        String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        int[] dayValues = {Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY};
        android.widget.CheckBox[] dayCheckBoxes = new android.widget.CheckBox[7];
        Set<Integer> selectedDays = route.getSchedule() != null ? route.getSchedule().getDaysOfWeek() : new HashSet<>();
        for (int i = 0; i < 7; i++) {
            dayCheckBoxes[i] = new android.widget.CheckBox(this);
            dayCheckBoxes[i].setText(dayNames[i]);
            if (selectedDays != null && selectedDays.contains(dayValues[i])) {
                dayCheckBoxes[i].setChecked(true);
            }
            daysLayout.addView(dayCheckBoxes[i]);
        }

        Button btnTime = new Button(this); btnTime.setText(getString(R.string.pick_time));
        final int[] hour = {route.getSchedule() != null ? route.getSchedule().getHour() : 8};
        final int[] minute = {route.getSchedule() != null ? route.getSchedule().getMinute() : 0};
        btnTime.setText(String.format(Locale.getDefault(), "%02d:%02d", hour[0], minute[0]));
        btnTime.setOnClickListener(v -> new TimePickerDialog(this, (TimePicker view, int h, int m) -> {
            hour[0] = h; minute[0] = m;
            btnTime.setText(String.format(Locale.getDefault(), "%02d:%02d", h, m));
        }, hour[0], minute[0], true).show());

        Button btnTest = new Button(this); btnTest.setText("Test");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.addView(etStart);
        layout.addView(etEnd);
        layout.addView(daysLayout, 2);
        layout.addView(btnTime);
        layout.addView(btnTest);
        builder.setView(layout);
        builder.setTitle("Edit Route");
        builder.setPositiveButton("Save", (dialog, which) -> {
            String start = etStart.getText().toString();
            String end = etEnd.getText().toString();
            String startPlaceId = startSuggestionPlaceIdMap.containsKey(start) ? startSuggestionPlaceIdMap.get(start) : route.getStartPlaceId();
            String endPlaceId = endSuggestionPlaceIdMap.containsKey(end) ? endSuggestionPlaceIdMap.get(end) : route.getEndPlaceId();
            if (start.isEmpty() || end.isEmpty()) {
                Toast.makeText(this, "Please enter both locations", Toast.LENGTH_SHORT).show();
                return;
            }
            if (startPlaceId == null || endPlaceId == null) {
                Toast.makeText(this, "Please select a valid address from suggestions", Toast.LENGTH_SHORT).show();
                return;
            }
            Set<Integer> newSelectedDays = new HashSet<>();
            for (int i = 0; i < 7; i++) {
                if (dayCheckBoxes[i].isChecked()) {
                    newSelectedDays.add(dayValues[i]);
                }
            }
            Schedule schedule = new Schedule(hour[0], minute[0], 1440, newSelectedDays);
            route.setStartLocation(start);
            route.setEndLocation(end);
            route.setStartPlaceId(startPlaceId);
            route.setEndPlaceId(endPlaceId);
            route.setSchedule(schedule);
            RouteManager.saveRoute(this, route);
            routes.set(position, route);
            adapter.notifyDataSetChanged();
            WorkManagerHelper.cancelRouteNotification(this, route); // Cancel previous schedule
            WorkManagerHelper.scheduleRouteNotification(this, route); // Schedule new one
            Toast.makeText(this, "Route updated", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        btnTest.setOnClickListener(v -> {
            String start = etStart.getText().toString();
            String end = etEnd.getText().toString();
            String startPlaceId = startSuggestionPlaceIdMap.containsKey(etStart.getText().toString()) ? startSuggestionPlaceIdMap.get(etStart.getText().toString()) : route.getStartPlaceId();
            String endPlaceId = endSuggestionPlaceIdMap.containsKey(etEnd.getText().toString()) ? endSuggestionPlaceIdMap.get(etEnd.getText().toString()) : route.getEndPlaceId();
            if (start.isEmpty() || end.isEmpty()) {
                Toast.makeText(this, "Please enter both locations", Toast.LENGTH_SHORT).show();
                return;
            }
            if (startPlaceId == null || endPlaceId == null) {
                Toast.makeText(this, "Please select a valid address from suggestions", Toast.LENGTH_SHORT).show();
                return;
            }
            PlacesRouteUtils.fetchRouteEtaAndDistanceWithPlaceIds(
                this,
                startPlaceId,
                endPlaceId,
                BuildConfig.GOOGLE_MAPS_API_KEY,
                message -> Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            );
        });
    }

    private void testRouteAndNotify(String start, String end, String startPlaceId, String endPlaceId) {
        if (start.isEmpty() || end.isEmpty()) {
            Toast.makeText(this, "Please enter both locations", Toast.LENGTH_SHORT).show();
            return;
        }
        if (startPlaceId == null || endPlaceId == null) {
            Toast.makeText(this, "Please select a valid address from suggestions", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Field> placeFields = Arrays.asList(Field.LOCATION);
        Task<FetchPlaceResponse> startTask = placesClient.fetchPlace(
                FetchPlaceRequest.newInstance(startPlaceId, placeFields)
        );
        Task<FetchPlaceResponse> endTask = placesClient.fetchPlace(
                FetchPlaceRequest.newInstance(endPlaceId, placeFields)
        );
        startTask.addOnSuccessListener(startResponse -> {
            Place startPlace = startResponse.getPlace();
            LatLng startLatLng = startPlace.getLocation();
            if (startLatLng == null) {
                Toast.makeText(this, "Could not resolve coordinates for start address.", Toast.LENGTH_LONG).show();
                return;
            }
            endTask.addOnSuccessListener(endResponse -> {
                Place endPlace = endResponse.getPlace();
                LatLng endLatLng = endPlace.getLocation();
                if (endLatLng == null) {
                    Toast.makeText(this, "Could not resolve coordinates for end address.", Toast.LENGTH_LONG).show();
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
                    message -> Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                );
            }).addOnFailureListener(e -> Toast.makeText(this, "Failed to resolve end address: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }).addOnFailureListener(e -> Toast.makeText(this, "Failed to resolve start address: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}
