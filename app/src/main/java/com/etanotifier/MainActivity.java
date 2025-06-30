package com.etanotifier;

import android.app.TimePickerDialog;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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
import com.etanotifier.util.AlarmManagerHelper;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.net.PlacesClient;

import android.widget.AutoCompleteTextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private List<Route> routes = new ArrayList<>();
    private RouteAdapter adapter;
    private PlacesClient placesClient;
    private AutocompleteSessionToken sessionToken;
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 1001;
    private ActivityResultLauncher<Intent> exactAlarmPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        exactAlarmPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {}
        );
        checkAndRequestExactAlarmPermission();

        ListView lvRoutes = findViewById(R.id.lvRoutes);
        Button btnAddRoute = findViewById(R.id.btnAddRoute);
        routes = RouteManager.getAllRoutes(this);
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
                Toast.makeText(MainActivity.this, "Route deleted", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onToggle(Route route, int position, boolean enabled) {
                route.setEnabled(enabled);
                RouteManager.saveRoute(MainActivity.this, route);
                if (enabled) {
                    AlarmManagerHelper.scheduleRouteNotification(MainActivity.this, route);
                }
                Toast.makeText(MainActivity.this, enabled ? "Route enabled" : "Route disabled", Toast.LENGTH_SHORT).show();
            }
        });

        placesClient = PlacesHelper.getPlacesClient(this, BuildConfig.GOOGLE_MAPS_API_KEY);
        sessionToken = PlacesHelper.getSessionToken();
        checkAndRequestNotificationPermission();
        btnAddRoute.setOnClickListener(v -> showAddRouteDialog());
    }

    private void checkAndRequestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (!alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                exactAlarmPermissionLauncher.launch(intent);
            }
        }
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
        java.util.Map<String, String> startSuggestionPlaceIdMap = new java.util.HashMap<>();
        java.util.Map<String, String> endSuggestionPlaceIdMap = new java.util.HashMap<>();
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
            btnTime.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", h, m));
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
            java.util.Set<Integer> selectedDays = new java.util.HashSet<>();
            for (int i = 0; i < 7; i++) {
                if (dayCheckBoxes[i].isChecked()) {
                    selectedDays.add(dayValues[i]);
                }
            }
            Schedule schedule = new Schedule(hour[0], minute[0], 1440, selectedDays);
            Route route = new Route(String.valueOf(System.currentTimeMillis()), start, end, startPlaceId, endPlaceId, schedule);
            routes.add(route);
            adapter.notifyDataSetChanged();
            AlarmManagerHelper.scheduleRouteNotification(this, route);
        });
        builder.setNegativeButton("Cancel", null);
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        btnTest.setOnClickListener(v -> testRouteAndNotify(etStart.getText().toString(), etEnd.getText().toString(), startSuggestionPlaceIdMap.get(etStart.getText().toString()), endSuggestionPlaceIdMap.get(etEnd.getText().toString())));
    }

    private void showEditRouteDialog(Route route, int position) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        AutoCompleteTextView etStart = new AutoCompleteTextView(this); etStart.setHint("Start location");
        AutoCompleteTextView etEnd = new AutoCompleteTextView(this); etEnd.setHint("End location");
        etStart.setText(route.getStartLocation());
        etEnd.setText(route.getEndLocation());
        java.util.Map<String, String> startSuggestionPlaceIdMap = new java.util.HashMap<>();
        java.util.Map<String, String> endSuggestionPlaceIdMap = new java.util.HashMap<>();
        PlacesHelper.setupAutocomplete(this, placesClient, sessionToken, etStart, startSuggestionPlaceIdMap);
        PlacesHelper.setupAutocomplete(this, placesClient, sessionToken, etEnd, endSuggestionPlaceIdMap);

        android.widget.GridLayout daysLayout = new android.widget.GridLayout(this);
        daysLayout.setColumnCount(3);
        String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        int[] dayValues = {Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY};
        android.widget.CheckBox[] dayCheckBoxes = new android.widget.CheckBox[7];
        java.util.Set<Integer> selectedDays = route.getSchedule() != null ? route.getSchedule().getDaysOfWeek() : new java.util.HashSet<>();
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
        btnTime.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", hour[0], minute[0]));
        btnTime.setOnClickListener(v -> new TimePickerDialog(this, (TimePicker view, int h, int m) -> {
            hour[0] = h; minute[0] = m;
            btnTime.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", h, m));
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
            java.util.Set<Integer> newSelectedDays = new java.util.HashSet<>();
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
            Toast.makeText(this, "Route updated", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        btnTest.setOnClickListener(v -> testRouteAndNotify(
            etStart.getText().toString(),
            etEnd.getText().toString(),
            startSuggestionPlaceIdMap.containsKey(etStart.getText().toString()) ? startSuggestionPlaceIdMap.get(etStart.getText().toString()) : route.getStartPlaceId(),
            endSuggestionPlaceIdMap.containsKey(etEnd.getText().toString()) ? endSuggestionPlaceIdMap.get(etEnd.getText().toString()) : route.getEndPlaceId()
        ));
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
        java.util.List<com.google.android.libraries.places.api.model.Place.Field> placeFields = java.util.Arrays.asList(com.google.android.libraries.places.api.model.Place.Field.LOCATION);
        com.google.android.gms.tasks.Task<com.google.android.libraries.places.api.net.FetchPlaceResponse> startTask = placesClient.fetchPlace(
                com.google.android.libraries.places.api.net.FetchPlaceRequest.newInstance(startPlaceId, placeFields)
        );
        com.google.android.gms.tasks.Task<com.google.android.libraries.places.api.net.FetchPlaceResponse> endTask = placesClient.fetchPlace(
                com.google.android.libraries.places.api.net.FetchPlaceRequest.newInstance(endPlaceId, placeFields)
        );
        startTask.addOnSuccessListener(startResponse -> {
            com.google.android.libraries.places.api.model.Place startPlace = startResponse.getPlace();
            com.google.android.gms.maps.model.LatLng startLatLng = startPlace.getLocation();
            if (startLatLng == null) {
                Toast.makeText(this, "Could not resolve coordinates for start address.", Toast.LENGTH_LONG).show();
                return;
            }
            endTask.addOnSuccessListener(endResponse -> {
                com.google.android.libraries.places.api.model.Place endPlace = endResponse.getPlace();
                com.google.android.gms.maps.model.LatLng endLatLng = endPlace.getLocation();
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
