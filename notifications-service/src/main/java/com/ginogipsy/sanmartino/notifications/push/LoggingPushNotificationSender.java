package com.ginogipsy.sanmartino.notifications.push;

import com.ginogipsy.sanmartino.observability.Logged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stub di default: scrive sul log invece di chiamare FCM. Attivo quando
 * sanmartino.fcm.enabled e' false o assente. Quando enabled=true subentra
 * FcmPushNotificationSender.
 */
@Component
@ConditionalOnProperty(name = "sanmartino.fcm.enabled", havingValue = "false", matchIfMissing = true)
@Logged
public class LoggingPushNotificationSender implements PushNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushNotificationSender.class);

    @Override
    public void sendToTopic(String topic, PushMessage message) {
        log.info("[PUSH STUB] topic={} title=\"{}\" body=\"{}\" data={}",
                topic, message.title(), message.body(), message.data());
    }
}
