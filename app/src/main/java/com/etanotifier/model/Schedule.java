package com.etanotifier.model;

import java.util.Calendar;
import java.util.Set;
import java.util.HashSet;

public class Schedule {
    private int hour;
    private int minute;
    private int repeatIntervalMinutes;
    private Set<Integer> daysOfWeek; // Calendar.SUNDAY ... Calendar.SATURDAY

    public Schedule(int hour, int minute, int repeatIntervalMinutes, Set<Integer> daysOfWeek) {
        this.hour = hour;
        this.minute = minute;
        this.repeatIntervalMinutes = repeatIntervalMinutes;
        this.daysOfWeek = daysOfWeek != null ? new HashSet<>(daysOfWeek) : null;
    }

    public int getHour() { return hour; }
    public int getMinute() { return minute; }
    public int getRepeatIntervalMinutes() { return repeatIntervalMinutes; }
    public Set<Integer> getDaysOfWeek() { return daysOfWeek; }

    public void setHour(int hour) { this.hour = hour; }
    public void setMinute(int minute) { this.minute = minute; }
    public void setRepeatIntervalMinutes(int repeatIntervalMinutes) { this.repeatIntervalMinutes = repeatIntervalMinutes; }
    public void setDaysOfWeek(Set<Integer> daysOfWeek) { this.daysOfWeek = daysOfWeek; }

    @Override
    public String toString() {
        return "Schedule{" +
                "hour=" + hour +
                ", minute=" + minute +
                ", repeatIntervalMinutes=" + repeatIntervalMinutes +
                ", daysOfWeek=" + daysOfWeek +
                '}';
    }

    public Calendar getNextScheduledTime() {
        Calendar now = Calendar.getInstance();
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        if (daysOfWeek != null && !daysOfWeek.isEmpty()) {
            int today = calendar.get(Calendar.DAY_OF_WEEK);
            int daysUntilNext = 0;
            while (!daysOfWeek.contains(calendar.get(Calendar.DAY_OF_WEEK)) || !calendar.after(now)) {
                calendar.add(Calendar.DATE, 1);
                daysUntilNext++;
                if (daysUntilNext > 7) break; // avoid infinite loop
            }
        } else if (calendar.before(now)) {
            calendar.add(Calendar.MINUTE, repeatIntervalMinutes);
        }
        return calendar;
    }
}
