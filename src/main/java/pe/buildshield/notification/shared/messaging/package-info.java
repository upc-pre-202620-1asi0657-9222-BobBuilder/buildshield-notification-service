/**
 * Eventos de integración entre microservicios: sobre JSON común ({@link pe.buildshield.notification.shared.messaging.EventEnvelope}),
 * Transactional Outbox ({@link pe.buildshield.notification.shared.messaging.OutboxWriter} y
 * {@link pe.buildshield.notification.shared.messaging.OutboxPublisher}) y consumo idempotente
 * ({@link pe.buildshield.notification.shared.messaging.IdempotentEventProcessor}). Entrega al menos una vez.
 */
package pe.buildshield.notification.shared.messaging;
