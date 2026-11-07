package pe.buildshield.notification.infrastructure.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pe.buildshield.notification.application.NotificationCommandService;
import pe.buildshield.notification.shared.messaging.IdempotentEventProcessor;

/**
 * Consumidor unificado de eventos de integración para el servicio de notificaciones (C4 pág. 29).
 * Escucha los tópicos de IAM, Organization, Ordering, Inventory, Discrepancy y Subscription.
 */
@Component
public class NotificationEventConsumer {

    public static final String CONSUMER = "NotificationEventConsumer";

    private final IdempotentEventProcessor processor;
    private final NotificationCommandService commands;

    public NotificationEventConsumer(IdempotentEventProcessor processor, NotificationCommandService commands) {
        this.processor = processor;
        this.commands = commands;
    }

    @KafkaListener(topics = {
            "${buildshield.messaging.consumes.iam:buildshield.iam}",
            "${buildshield.messaging.consumes.organization:buildshield.organization}",
            "${buildshield.messaging.consumes.ordering:buildshield.ordering}",
            "${buildshield.messaging.consumes.inventory:buildshield.inventory}",
            "${buildshield.messaging.consumes.discrepancy:buildshield.discrepancy}",
            "${buildshield.messaging.consumes.subscription:buildshield.subscription}"
    })
    public void onMessage(ConsumerRecord<String, String> record) {
        processor.process(CONSUMER, record, commands::handle);
    }
}
