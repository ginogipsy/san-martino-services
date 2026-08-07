package com.ginogipsy.sanmartino.notifications.push;

import com.ginogipsy.sanmartino.observability.Logged;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("fcmPushNotificationSender")
@ConditionalOnProperty(name = "sanmartino.fcm.enabled", havingValue = "true")
// @Logged: notifications-service non ha @Service ne' @RestController, quindi senza
// questa annotazione l'aspect non vedrebbe la chiamata verso FCM — che e' l'unica
// dipendenza esterna del servizio, e quindi il punto dove servono durata ed esito.
@Logged
public class FcmPushNotificationSender implements PushNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushNotificationSender.class);

    private final FirebaseMessaging messaging;

    public FcmPushNotificationSender(FirebaseApp firebaseApp) {
        this.messaging = FirebaseMessaging.getInstance(firebaseApp);
    }

    @Override
    public void sendToTopic(String topic, PushMessage message) {
        Message fcmMessage = Message.builder()
                .setTopic(topic)
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(message.data())
                .build();

        try {
            String messageId = messaging.send(fcmMessage);
            log.info("FCM push sent to topic '{}' (messageId={})", topic, messageId);
        } catch (FirebaseMessagingException ex) {
            log.error("FCM push to topic '{}' failed: {} (errorCode={})",
                    topic, ex.getMessage(), ex.getErrorCode());
        }
    }
}
