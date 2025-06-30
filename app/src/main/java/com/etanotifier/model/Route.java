package com.etanotifier.model;

public class Route {
    private String id;
    private String startLocation;
    private String endLocation;
    private String startPlaceId;
    private String endPlaceId;
    private Schedule schedule;
    private boolean enabled = true;

    public Route(String id, String startLocation, String endLocation, String startPlaceId, String endPlaceId, Schedule schedule) {
        this(id, startLocation, endLocation, startPlaceId, endPlaceId, schedule, true);
    }

    public Route(String id, String startLocation, String endLocation, String startPlaceId, String endPlaceId, Schedule schedule, boolean enabled) {
        this.id = id;
        this.startLocation = startLocation;
        this.endLocation = endLocation;
        this.startPlaceId = startPlaceId;
        this.endPlaceId = endPlaceId;
        this.schedule = schedule;
        this.enabled = enabled;
    }

    public String getId() { return id; }
    public String getStartLocation() { return startLocation; }
    public String getEndLocation() { return endLocation; }
    public String getStartPlaceId() { return startPlaceId; }
    public String getEndPlaceId() { return endPlaceId; }
    public Schedule getSchedule() { return schedule; }
    public boolean isEnabled() { return enabled; }

    public void setId(String id) { this.id = id; }
    public void setStartLocation(String startLocation) { this.startLocation = startLocation; }
    public void setEndLocation(String endLocation) { this.endLocation = endLocation; }
    public void setStartPlaceId(String startPlaceId) { this.startPlaceId = startPlaceId; }
    public void setEndPlaceId(String endPlaceId) { this.endPlaceId = endPlaceId; }
    public void setSchedule(Schedule schedule) { this.schedule = schedule; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
