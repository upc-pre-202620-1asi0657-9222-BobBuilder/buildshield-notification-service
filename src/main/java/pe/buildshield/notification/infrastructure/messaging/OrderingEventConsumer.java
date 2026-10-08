package pe.buildshield.notification.infrastructure.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pe.buildshield.notification.application.NotificationCommandService;
import pe.buildshield.notification.shared.messaging.IdempotentEventProcessor;

/** Consume {@code buildshield.ordering}: salida, anulación e incidencias del despacho y recepción confirmada. */
@Component
class OrderingEventConsumer {

    private final IdempotentEventProcessor processor;
    private final NotificationCommandService commands;

    OrderingEventConsumer(IdempotentEventProcessor processor, NotificationCommandService commands) {
        this.processor = processor;
        this.commands = commands;
    }

    @KafkaListener(topics = "${buildshield.messaging.consumes.ordering:buildshield.ordering}")
    void onMessage(ConsumerRecord<String, String> record) {
        processor.process(record, commands::handle);
    }
}
