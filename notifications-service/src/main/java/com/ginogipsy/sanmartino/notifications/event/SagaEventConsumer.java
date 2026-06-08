package com.ginogipsy.sanmartino.notifications.event;

import com.ginogipsy.sanmartino.notifications.push.PushNotificationSender;
import com.ginogipsy.sanmartino.notifications.push.PushNotificationSender.PushMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SagaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SagaEventConsumer.class);

    private final PushNotificationSender pushSender;

    public SagaEventConsumer(PushNotificationSender pushSender) {
        this.pushSender = pushSender;
    }

    @KafkaListener(
            topics = "${sanmartino.kafka.topics.saga-events:sanmartino.saga.events}",
            groupId = "${spring.kafka.consumer.group-id:notifications-service}"
    )
    public void onSagaEvent(SagaEvent event) {
        log.info("Received saga event: sagaId={} type={} status={} eventId={}",
                event.sagaId(), event.sagaType(), event.status(), event.eventId());

        switch (event.status()) {
            case "COMPLETED" -> handleCompleted(event);
            case "COMPENSATED" -> handleCompensated(event);
            default -> log.warn("Unknown saga status: {}", event.status());
        }
    }

    private void handleCompleted(SagaEvent event) {
        if ("CreateEventWithStands".equals(event.sagaType())) {
            PushMessage message = new PushMessage(
                    "Nuova edizione di San Martino!",
                    "Ci sono novita' per la prossima festa. Apri l'app per scoprirle.",
                    Map.of(
                            "eventId", String.valueOf(event.eventId()),
                            "type", "new-edition"
                    )
            );
            pushSender.sendToTopic("all-users", message);
        }
    }

    private void handleCompensated(SagaEvent event) {
        log.warn("Saga {} compensated (no push sent). Root cause: {}",
                event.sagaId(), event.message());
        // In futuro: notifica solo agli admin / dashboard, non agli end user
    }
}
