package com.ginogipsy.sanmartino.notifications.push;

public interface PushNotificationSender {

    /**
     * Invia una notifica push a un topic FCM (es. "all-users", "stand-owners")
     * o a uno specifico device token. Per ora il topic e' l'astrazione che
     * useremo per il broadcast (Android client subscribe al topic).
     */
    void sendToTopic(String topic, PushMessage message);

    record PushMessage(String title, String body, java.util.Map<String, String> data) {
        public PushMessage(String title, String body) {
            this(title, body, java.util.Map.of());
        }
    }
}
