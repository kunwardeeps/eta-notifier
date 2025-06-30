package com.etanotifier.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import com.etanotifier.model.Route;
import com.etanotifier.receiver.RouteAlarmReceiver;
import com.etanotifier.route.RouteManager;
import java.util.Calendar;

public class AlarmManagerHelper {
    public static void scheduleRouteNotification(Context context, Route route) {
        Calendar cal = route.getSchedule().getNextScheduledTime();
        Intent intent = new Intent(context, RouteAlarmReceiver.class);
        intent.putExtra("route_id", route.getId());
        RouteManager.saveRoute(context, route);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                route.getId().hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        try {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
                Toast.makeText(context, "Notification scheduled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Cannot schedule exact alarms. Permission not granted.", Toast.LENGTH_LONG).show();
            }
        } catch (SecurityException e) {
            Toast.makeText(context, "Failed to schedule alarm: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static void cancelRouteNotification(Context context, Route route) {
        Intent intent = new Intent(context, RouteAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                route.getId().hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);
    }
}

