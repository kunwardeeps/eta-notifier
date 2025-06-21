package com.example.etanotifier.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.example.etanotifier.model.Route;
import java.util.List;

public class RouteAdapter extends BaseAdapter {
    private Context context;
    private List<Route> routes;

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
            convertView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, parent, false);
        }
        TextView text1 = convertView.findViewById(android.R.id.text1);
        TextView text2 = convertView.findViewById(android.R.id.text2);
        Route route = routes.get(position);
        text1.setText(route.getStartLocation() + " → " + route.getEndLocation());
        text2.setText("Schedule: " + route.getSchedule().getHour() + ":" + String.format("%02d", route.getSchedule().getMinute()));
        return convertView;
    }
}
