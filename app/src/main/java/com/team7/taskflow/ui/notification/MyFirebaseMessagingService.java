package com.team7.taskflow.ui.notification;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.team7.taskflow.data.repository.DeviceRepository;
import com.team7.taskflow.domain.model.Notification;
import com.team7.taskflow.utils.SessionManager;

/**
 * Service to receive FCM messages and deliver them to app.
 * Also responsible for sending device token to backend (Supabase) via DeviceRepository.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "FCM new token: " + (token != null ? "[RECEIVED]" : "null"));

        // Save/send token to backend if user is logged in
        try {
            String userId = SessionManager.getUserId();
            if (userId != null && !userId.isEmpty()) {
                DeviceRepository.getInstance().upsertDeviceToken(userId, token, new DeviceRepository.ResultCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Device token upserted successfully");
                    }

                    @Override
                    public void onError(String message) {
                        Log.w(TAG, "Failed to upsert device token: " + message);
                    }
                });
            }
        } catch (Exception e) {
            Log.w(TAG, "Error while upserting device token", e);
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        // Delegate display to existing NotificationPushDispatcher for consistency
        try {
            Log.d(TAG, "Message received: data=" + remoteMessage.getData());

            // Build minimal Notification object from data payload or notification body
            Notification n = new Notification();
            try {
                String nid = remoteMessage.getData().get("notification_id");
                if (nid != null) n.setNotificationId(Long.parseLong(nid));
            } catch (NumberFormatException ignored) {}

            String type = remoteMessage.getData().get("type");
            if (type == null && remoteMessage.getNotification() != null) {
                // fallback: try to infer from title (not reliable)
                type = "SYSTEM_ALERT";
            }
            if (type != null) n.setTypeRaw(type);

            String actorId = remoteMessage.getData().get("actor_id");
            if (actorId != null) n.setActorId(actorId);

            String ref = remoteMessage.getData().get("reference_id");
            try {
                if (ref != null) n.setReferenceId(Long.parseLong(ref));
            } catch (NumberFormatException ignored) {}

            String body = remoteMessage.getNotification() != null ? remoteMessage.getNotification().getBody() : remoteMessage.getData().get("body");
            if (body != null) n.setDisplayContent(body);

            NotificationPushDispatcher.showFromRemote(getApplicationContext(), n);
        } catch (Exception e) {
            Log.w(TAG, "Error processing incoming FCM message", e);
        }
    }
}
