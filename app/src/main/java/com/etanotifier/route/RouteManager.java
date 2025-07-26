package com.etanotifier.route;

import android.content.Context;
import com.etanotifier.model.Route;
import java.util.List;

public class RouteManager {
    public static List<Route> getAllRoutes(Context context) {
        return RouteStorage.getAllRoutes(context);
    }
    public static void saveRoute(Context context, Route route) {
        RouteStorage.saveRoute(context, route);
    }
    public static void deleteRoute(Context context, String routeId) {
        RouteStorage.deleteRoute(context, routeId);
    }
    public static Route getRouteById(Context context, String routeId) {
        List<Route> routes = getAllRoutes(context);
        for (Route route : routes) {
            if (route.getId().equals(routeId)) {
                return route;
            }
        }
        return null;
    }
}
