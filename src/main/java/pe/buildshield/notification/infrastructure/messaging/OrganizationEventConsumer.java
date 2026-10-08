package pe.buildshield.notification.infrastructure.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pe.buildshield.notification.application.NotificationCommandService;
import pe.buildshield.notification.shared.messaging.IdempotentEventProcessor;

/** Consume {@code buildshield.organization}: nombres de obras y almacenes y asignaciones de encargados. */
@Component
class OrganizationEventConsumer {

    private final IdempotentEventProcessor processor;
    private final NotificationCommandService commands;

    OrganizationEventConsumer(IdempotentEventProcessor processor, NotificationCommandService commands) {
        this.processor = processor;
        this.commands = commands;
    }

    @KafkaListener(topics = "${buildshield.messaging.consumes.organization:buildshield.organization}")
    void onMessage(ConsumerRecord<String, String> record) {
        TenantScopedEvents.process(processor, record, commands::handle);
    }
}
