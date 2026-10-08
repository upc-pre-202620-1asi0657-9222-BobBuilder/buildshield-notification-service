package pe.buildshield.notification.infrastructure.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.buildshield.notification.application.EmailPort;

/**
 * Adaptador de SendGrid para {@link EmailPort}. En local y en pruebas solo registra el correo en el log (sin llamar a
 * SendGrid ni necesitar una clave). El envío real se activa cuando el equipo tenga la cuenta de SendGrid.
 */
@Component
class SendGridAdapter implements EmailPort {

    private static final Logger log = LoggerFactory.getLogger(SendGridAdapter.class);

    private final boolean enabled;
    private final String from;

    SendGridAdapter(@Value("${buildshield.notification.email.enabled:false}") boolean enabled,
            @Value("${buildshield.notification.email.from:notificaciones@buildshield.pe}") String from) {
        this.enabled = enabled;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (enabled) {
            log.warn("SendGrid todavía no está configurado: el correo a {} solo se registra", to);
        }
        log.info("Correo de {} para {}: {} | {}", from, to, subject, body);
    }
}
