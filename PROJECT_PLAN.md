# Project: ETA Notifier

## App Flow Overview

1. **User Input**: User selects a notification time in the app UI.
2. **Scheduling**: The app schedules a notification using Android's WorkManager.
3. **Persistence**: Notification details are saved in a local Room database for reliability.
4. **Notification Trigger**: At the scheduled time, WorkManager executes the worker to show a notification.
5. **Notification Display**: Android's NotificationManager displays the notification to the user.
6. **History/Management**: Users can view, edit, or delete scheduled notifications.

## Notification Lifecycle

- **Scheduling**: When a user selects a time, the app schedules a notification and persists it.
- **Persistence**: All scheduled notifications are stored in the database, ensuring they survive app restarts.
- **Triggering**: At the scheduled time, WorkManager runs the worker, which shows the notification.
- **Notification**: The app uses NotificationManager to display the notification.
- **Management**: Users can manage notifications (view, edit, delete) via the app UI.

## Main Classes and Their Purpose

- **MainActivity**: Handles user input, displays scheduled notifications, and manages navigation.
- **NotificationScheduler**: Schedules notifications using WorkManager only.
- **NotificationWorker**: Worker class executed by WorkManager to trigger notification display.
- **NotificationRepository**: Manages notification data persistence using Room.
- **NotificationEntity**: Data model representing a scheduled notification (time, message, status).
- **NotificationDao**: Provides CRUD operations for notifications in the database.
- **NotificationManagerHelper**: Builds and displays notifications using NotificationManager.

## Code Walkthrough & Functionality

1. **User schedules a notification**: MainActivity collects input and passes it to NotificationScheduler.
2. **Notification is persisted**: NotificationRepository saves the notification in the Room database.
3. **WorkManager schedules the worker**: NotificationScheduler schedules the NotificationWorker.
4. **Worker triggers**: NotificationWorker runs at the scheduled time and calls NotificationManagerHelper.
5. **Notification is displayed**: NotificationManagerHelper shows the notification.
6. **User manages notifications**: MainActivity interacts with NotificationRepository to update or delete notifications.

---

This flow ensures notifications are reliably scheduled, persisted, and displayed using WorkManager, with clear responsibilities for each class.
