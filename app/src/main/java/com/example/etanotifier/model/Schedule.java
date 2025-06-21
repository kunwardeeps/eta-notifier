package com.example.etanotifier.model;

import java.util.Calendar;

public class Schedule {
    private int hour;
    private int minute;
    private int repeatIntervalMinutes;

    public Schedule(int hour, int minute, int repeatIntervalMinutes) {
        this.hour = hour;
        this.minute = minute;
        this.repeatIntervalMinutes = repeatIntervalMinutes;
    }

    public int getHour() { return hour; }
    public int getMinute() { return minute; }
    public int getRepeatIntervalMinutes() { return repeatIntervalMinutes; }

    public void setHour(int hour) { this.hour = hour; }
    public void setMinute(int minute) { this.minute = minute; }
    public void setRepeatIntervalMinutes(int repeatIntervalMinutes) { this.repeatIntervalMinutes = repeatIntervalMinutes; }

    public Calendar getNextScheduledTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.MINUTE, repeatIntervalMinutes);
        }
        return calendar;
    }
}
