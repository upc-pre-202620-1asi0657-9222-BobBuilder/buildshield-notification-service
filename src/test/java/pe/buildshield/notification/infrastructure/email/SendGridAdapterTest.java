package pe.buildshield.notification.infrastructure.email;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SendGridAdapterTest {

    @Test
    void in_local_the_email_is_only_logged() {
        assertThatCode(() -> new SendGridAdapter(false, "no-reply@buildshield.pe")
                .send("luis@obra.pe", "Asunto", "Cuerpo")).doesNotThrowAnyException();
        assertThatCode(() -> new SendGridAdapter(true, "no-reply@buildshield.pe")
                .send("luis@obra.pe", "Asunto", "Cuerpo")).doesNotThrowAnyException();
    }

    @Test
    void circuit_breaker_blocks_execution_when_open() {
        CircuitBreaker cb = SendGridAdapter.defaultCircuitBreaker();
        SendGridAdapter adapter = new SendGridAdapter(false, "no-reply@buildshield.pe", cb);

        cb.transitionToOpenState();

        assertThatThrownBy(() -> adapter.send("luis@obra.pe", "Asunto", "Cuerpo"))
                .isInstanceOf(CallNotPermittedException.class);
    }
}
