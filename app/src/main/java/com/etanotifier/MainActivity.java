package com.etanotifier;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TimePicker;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.QueryPurchasesParams;
import com.android.billingclient.api.QueryProductDetailsParams;
import android.content.SharedPreferences;

public class MainActivity extends AppCompatActivity implements PurchasesUpdatedListener {
    private List<Route> routes = new ArrayList<>();
    private RouteAdapter adapter;
    private PlacesClient placesClient;
    private AutocompleteSessionToken sessionToken;
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 1001;
    private AdView mAdView;
    private BillingClient billingClient;
    private boolean isAdFree = false;
    private static final String SKU_AD_FREE = "ad_free";
    private SharedPreferences prefs;
    private Button btnGoAdFree;

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
                Toast.makeText(MainActivity.this, getString(R.string.route_deleted), Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onToggle(Route route, int position, boolean enabled) {
                route.setEnabled(enabled);
                RouteManager.saveRoute(MainActivity.this, route);
                if (enabled) {
                    WorkManagerHelper.scheduleRouteNotification(MainActivity.this, route);
                }
                Toast.makeText(MainActivity.this, enabled ? getString(R.string.route_enabled) : getString(R.string.route_disabled), Toast.LENGTH_SHORT).show();
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
        btnGoAdFree = findViewById(R.id.btnGoAdFree);
        btnGoAdFree.setVisibility(View.VISIBLE);
        btnGoAdFree.setOnClickListener(v -> launchPurchaseFlow());
        prefs = getSharedPreferences("iap_prefs", MODE_PRIVATE);
        isAdFree = prefs.getBoolean("ad_free", false);
        mAdView = findViewById(R.id.adView);
        if (isAdFree) {
            mAdView.setVisibility(View.GONE);
            btnGoAdFree.setVisibility(View.GONE);
        } else {
            MobileAds.initialize(this, initializationStatus -> {});
            AdRequest adRequest = new AdRequest.Builder().build();
            mAdView.loadAd(adRequest);
        }
        setupBillingClient();
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

    private void setupBillingClient() {
        billingClient = BillingClient.newBuilder(this)
            .setListener(this)
            .enablePendingPurchases()
            .build();
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryPurchasesAsync();
                }
            }
            @Override
            public void onBillingServiceDisconnected() {}
        });
    }
    private void queryPurchasesAsync() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
            (billingResult, purchasesList) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchasesList != null) {
                    for (Purchase purchase : purchasesList) {
                        if (purchase.getProducts().contains(SKU_AD_FREE) && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                            setAdFreePurchased();
                        }
                    }
                }
            }
        );
    }
    private void launchPurchaseFlow() {
        if (billingClient == null || !billingClient.isReady()) {
            Toast.makeText(this, "Billing service is not ready. Please try again later.", Toast.LENGTH_LONG).show();
            Log.e("Billing", "BillingClient not ready");
            return;
        }
        List<QueryProductDetailsParams.Product> productList = Arrays.asList(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_AD_FREE)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        );
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build();
        billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsList != null && !productDetailsList.isEmpty()) {
                ProductDetails productDetails = productDetailsList.get(0);
                List<BillingFlowParams.ProductDetailsParams> productDetailsParamsList = Arrays.asList(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                );
                BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList)
                    .build();
                int responseCode = billingClient.launchBillingFlow(this, flowParams).getResponseCode();
                if (responseCode != BillingClient.BillingResponseCode.OK) {
                    Toast.makeText(this, "Unable to start purchase flow. Response code: " + responseCode, Toast.LENGTH_LONG).show();
                    Log.e("Billing", "launchBillingFlow failed: " + responseCode);
                }
            } else {
                Toast.makeText(this, "Ad-Free product not available. Please try again later.", Toast.LENGTH_LONG).show();
                Log.e("Billing", "Product details not found or billing error: " + billingResult.getResponseCode());
            }
        });
    }
    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                if (purchase.getProducts().contains(SKU_AD_FREE) && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    setAdFreePurchased();
                }
            }
        }
    }
    private void setAdFreePurchased() {
        isAdFree = true;
        prefs.edit().putBoolean("ad_free", true).apply();
        if (mAdView != null) mAdView.setVisibility(View.GONE);
        if (btnGoAdFree != null) btnGoAdFree.setVisibility(View.GONE);
        Toast.makeText(this, "Ad-Free unlocked!", Toast.LENGTH_LONG).show();
    }
}
