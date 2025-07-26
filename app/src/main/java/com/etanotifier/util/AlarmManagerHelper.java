package com.etanotifier.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.etanotifier.model.Route;
import com.etanotifier.receiver.RouteAlarmReceiver;
import java.util.Calendar;

public class AlarmManagerHelper {
    private static final String EXTRA_ROUTE_ID = "route_id";

    public static void scheduleRouteNotification(Context context, Route route) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, RouteAlarmReceiver.class);
        intent.putExtra(EXTRA_ROUTE_ID, route.getId());

        // Use FLAG_UPDATE_CURRENT to ensure the intent extras are preserved
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context,
            route.getId().hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Calculate the next alarm time based on the route schedule
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, route.getSchedule().getHour());
        calendar.set(Calendar.MINUTE, route.getSchedule().getMinute());
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // If the time has already passed today, schedule for tomorrow
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Schedule the exact alarm if possible, otherwise use inexact
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                pendingIntent
            );
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                pendingIntent
            );
        }
    }

    public static void cancelRouteNotification(Context context, Route route) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, RouteAlarmReceiver.class);
        intent.putExtra(EXTRA_ROUTE_ID, route.getId());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context,
            route.getId().hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(pendingIntent);
        pendingIntent.cancel();
    }
}
