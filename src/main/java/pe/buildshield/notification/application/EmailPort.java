package pe.buildshield.notification.application;

/** Puerto del correo de los avisos. En la arquitectura v4 lo implementa SendGrid. */
public interface EmailPort {

    void send(String to, String subject, String body);
}
