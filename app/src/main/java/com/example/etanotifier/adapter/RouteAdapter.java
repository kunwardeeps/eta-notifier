package com.example.etanotifier.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.ImageButton;
import androidx.appcompat.widget.SwitchCompat;
import com.example.etanotifier.R;
import com.example.etanotifier.model.Route;
import com.example.etanotifier.model.Schedule;
import java.util.List;

public class RouteAdapter extends BaseAdapter {
    private final Context context;
    private final List<Route> routes;

    public interface RouteActionListener {
        void onEdit(Route route, int position);
        void onDelete(Route route, int position);
        void onToggle(Route route, int position, boolean enabled);
    }

    private RouteActionListener actionListener;

    public void setRouteActionListener(RouteActionListener listener) {
        this.actionListener = listener;
    }

    public RouteAdapter(Context context, List<Route> routes) {
        this.context = context;
        this.routes = routes;
    }

    @Override
    public int getCount() { return routes.size(); }

    @Override
    public Object getItem(int position) { return routes.get(position); }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_route, parent, false);
        }
        TextView tvRouteTitle = convertView.findViewById(R.id.tvRouteTitle);
        TextView tvRouteSchedule = convertView.findViewById(R.id.tvRouteSchedule);
        ImageButton btnEdit = convertView.findViewById(R.id.btnEdit);
        ImageButton btnDelete = convertView.findViewById(R.id.btnDelete);
        SwitchCompat switchEnable = convertView.findViewById(R.id.switchEnable);
        Route route = routes.get(position);
        tvRouteTitle.setText(context.getString(R.string.route_title_format, route.getStartLocation(), route.getEndLocation()));

        // Format schedule with days and AM/PM
        Schedule schedule = route.getSchedule();
        StringBuilder daysBuilder = new StringBuilder();
        if (schedule != null && schedule.getDaysOfWeek() != null && !schedule.getDaysOfWeek().isEmpty()) {
            for (Integer day : schedule.getDaysOfWeek()) {
                switch (day) {
                    case java.util.Calendar.SUNDAY: daysBuilder.append("Sun "); break;
                    case java.util.Calendar.MONDAY: daysBuilder.append("Mon "); break;
                    case java.util.Calendar.TUESDAY: daysBuilder.append("Tue "); break;
                    case java.util.Calendar.WEDNESDAY: daysBuilder.append("Wed "); break;
                    case java.util.Calendar.THURSDAY: daysBuilder.append("Thu "); break;
                    case java.util.Calendar.FRIDAY: daysBuilder.append("Fri "); break;
                    case java.util.Calendar.SATURDAY: daysBuilder.append("Sat "); break;
                }
            }
        }
        int hour = schedule != null ? schedule.getHour() : 0;
        int minute = schedule != null ? schedule.getMinute() : 0;
        String ampm = hour >= 12 ? "PM" : "AM";
        int hour12 = hour % 12 == 0 ? 12 : hour % 12;
        String scheduleText = String.format("%s%02d:%02d %s", daysBuilder.toString(), hour12, minute, ampm);
        tvRouteSchedule.setText(scheduleText.trim());

        btnEdit.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onEdit(route, position);
        });
        btnDelete.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onDelete(route, position);
        });
        switchEnable.setOnCheckedChangeListener(null);
        switchEnable.setChecked(route.isEnabled());
        switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (actionListener != null) actionListener.onToggle(route, position, isChecked);
        });
        return convertView;
    }
}
