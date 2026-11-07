package pe.buildshield.notification.infrastructure.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import pe.buildshield.notification.application.NotificationCommandService;
import pe.buildshield.notification.shared.messaging.IdempotentEventProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationEventConsumerTest {

    @Test
    void onMessage_delegates_to_idempotent_processor_with_expected_consumer_name() {
        IdempotentEventProcessor processor = mock(IdempotentEventProcessor.class);
        NotificationCommandService commands = mock(NotificationCommandService.class);
        NotificationEventConsumer consumer = new NotificationEventConsumer(processor, commands);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("buildshield.ordering", 0, 10L, "key", "payload");
        consumer.onMessage(record);

        verify(processor).process(eq("NotificationEventConsumer"), eq(record), any());
    }
}
