/**
 * Microservicio Notificaciones (contexto genérico): bandeja en la aplicación y correo, alimentados solo por eventos
 * de iam, organization, ordering y discrepancy. No publica eventos.
 *
 * <p>Capas: {@code interfaces} (REST) → {@code application} (casos de uso) → {@code domain} (Java puro);
 * {@code infrastructure} implementa los puertos (JPA, JDBC, Kafka, SendGrid). {@code shared} es el kernel compartido
 * copiado de buildshield-iam-service.
 */
package pe.buildshield.notification;
