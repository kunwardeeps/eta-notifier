package com.example.etanotifier;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TimePicker;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.etanotifier.adapter.RouteAdapter;
import com.example.etanotifier.model.Route;
import com.example.etanotifier.model.Schedule;
import com.example.etanotifier.receiver.RouteAlarmReceiver;
import com.example.etanotifier.util.NotificationScheduler;
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
        // Simple dialog for demo: use EditTexts for start/end, time picker for schedule
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
        EditText etStart = new EditText(this); etStart.setHint("Start location");
        EditText etEnd = new EditText(this); etEnd.setHint("End location");
        Button btnTime = new Button(this); btnTime.setText("Pick time");
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
        builder.show();
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
}
