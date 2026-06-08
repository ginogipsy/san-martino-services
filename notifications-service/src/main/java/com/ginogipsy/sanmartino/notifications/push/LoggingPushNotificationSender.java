package com.ginogipsy.sanmartino.notifications.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Stub default per il sender di push notifications: scrive sul log invece di
 * chiamare FCM. Resta attivo finche' non c'e' un'altra implementazione (es.
 * FcmPushNotificationSender) registrata nel context.
 *
 * Quando avrai le credenziali Firebase:
 * 1. Decomenta firebase-admin nel pom.xml di notifications-service
 * 2. Aggiungi il path al service-account.json in application.yaml
 *    (sanmartino.fcm.credentials-path)
 * 3. Crea FcmPushNotificationSender che implementa PushNotificationSender
 *    e annotalo con @Primary o assicurati che venga preferito a questo stub.
 */
@Component
@ConditionalOnMissingBean(name = "fcmPushNotificationSender")
public class LoggingPushNotificationSender implements PushNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushNotificationSender.class);

    @Override
    public void sendToTopic(String topic, PushMessage message) {
        log.info("[PUSH STUB] topic={} title=\"{}\" body=\"{}\" data={}",
                topic, message.title(), message.body(), message.data());
    }
}
