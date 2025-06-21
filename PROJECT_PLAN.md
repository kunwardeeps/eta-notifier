# Project: ETA Notifier

This Android app allows users to add routes (start and end locations) with custom notification schedules. On the scheduled time, the app fetches ETA and distance from Google Maps Route API and notifies the user.

## Main Modules
- app/src/main/java/com/example/etanotifier/
  - model/Route.java
  - model/Schedule.java
  - network/GoogleMapsApiService.java
  - repository/RouteRepository.java
  - ui/MainActivity.java
  - ui/AddRouteActivity.java
  - util/NotificationHelper.java
  - worker/RouteCheckWorker.java

## Next Steps
1. Create the Android project structure.
2. Implement the Java classes for models, networking, repository, UI, notifications, and background worker.
3. Integrate with Google Maps API.
4. Set up notification scheduling.

---

This file is for planning. The actual code will be in the respective Java files and Android resources.
