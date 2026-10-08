package pe.buildshield.notification.infrastructure.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pe.buildshield.notification.application.NotificationCommandService;
import pe.buildshield.notification.shared.messaging.IdempotentEventProcessor;

/** Consume {@code buildshield.iam}: {@code UserCreated} y {@code UserUpdated} mantienen la copia de destinatarios. */
@Component
class IamEventConsumer {

    private final IdempotentEventProcessor processor;
    private final NotificationCommandService commands;

    IamEventConsumer(IdempotentEventProcessor processor, NotificationCommandService commands) {
        this.processor = processor;
        this.commands = commands;
    }

    @KafkaListener(topics = "${buildshield.messaging.consumes.iam:buildshield.iam}")
    void onMessage(ConsumerRecord<String, String> record) {
        processor.process(record, commands::handle);
    }
}
