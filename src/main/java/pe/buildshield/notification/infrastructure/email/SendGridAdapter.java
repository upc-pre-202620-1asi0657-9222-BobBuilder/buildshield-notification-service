package pe.buildshield.notification.infrastructure.email;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.buildshield.notification.application.EmailPort;

import java.time.Duration;

/**
 * Adaptador de SendGrid para {@link EmailPort} con Circuit Breaker de Resilience4j (C4 pág. 29).
 * En local y en pruebas solo registra el correo en el log (sin llamar a SendGrid ni necesitar una clave).
 * Si el circuit breaker está abierto, arroja {@link io.github.resilience4j.circuitbreaker.CallNotPermittedException}
 * para que {@code NotificationCommandService} conserve la notificación en {@code PENDING} y no se bloquee.
 */
@Component
public class SendGridAdapter implements EmailPort {

    private static final Logger log = LoggerFactory.getLogger(SendGridAdapter.class);

    private final boolean enabled;
    private final String from;
    private final CircuitBreaker circuitBreaker;

    @Autowired
    public SendGridAdapter(@Value("${buildshield.notification.email.enabled:false}") boolean enabled,
            @Value("${buildshield.notification.email.from:notificaciones@buildshield.pe}") String from,
            @Autowired(required = false) CircuitBreaker circuitBreaker) {
        this.enabled = enabled;
        this.from = from;
        this.circuitBreaker = circuitBreaker != null ? circuitBreaker : defaultCircuitBreaker();
    }

    public SendGridAdapter(boolean enabled, String from) {
        this(enabled, from, defaultCircuitBreaker());
    }

    public SendGridAdapter(CircuitBreaker circuitBreaker) {
        this(false, "notificaciones@buildshield.pe", circuitBreaker);
    }

    public static CircuitBreaker defaultCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(5)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();
        return CircuitBreaker.of("sendgrid", config);
    }

    public CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    @Override
    public void send(String to, String subject, String body) {
        circuitBreaker.executeRunnable(() -> doSend(to, subject, body));
    }

    protected void doSend(String to, String subject, String body) {
        if (enabled) {
            log.warn("SendGrid todavía no está configurado: el correo a {} solo se registra", to);
        }
        log.info("Correo de {} para {}: {} | {}", from, to, subject, body);
    }
}
