package pe.buildshield.notification.infrastructure.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class SendGridAdapterTest {

    @Test
    void in_local_the_email_is_only_logged() {
        assertThatCode(() -> new SendGridAdapter(false, "no-reply@buildshield.pe")
                .send("luis@obra.pe", "Asunto", "Cuerpo")).doesNotThrowAnyException();
        assertThatCode(() -> new SendGridAdapter(true, "no-reply@buildshield.pe")
                .send("luis@obra.pe", "Asunto", "Cuerpo")).doesNotThrowAnyException();
    }
}
