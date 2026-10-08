package pe.buildshield.notification.infrastructure.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pe.buildshield.notification.application.NotificationCommandService;
import pe.buildshield.notification.shared.messaging.IdempotentEventProcessor;

/** Consume {@code buildshield.discrepancy}: respuesta, escalamiento, resolución y aceptación de discrepancias. */
@Component
class DiscrepancyEventConsumer {

    private final IdempotentEventProcessor processor;
    private final NotificationCommandService commands;

    DiscrepancyEventConsumer(IdempotentEventProcessor processor, NotificationCommandService commands) {
        this.processor = processor;
        this.commands = commands;
    }

    @KafkaListener(topics = "${buildshield.messaging.consumes.discrepancy:buildshield.discrepancy}")
    void onMessage(ConsumerRecord<String, String> record) {
        processor.process(record, commands::handle);
    }
}
