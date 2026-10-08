package pe.buildshield.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Notificaciones de BuildShield (microservicio, puerto 8087, base notification_db). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BuildshieldNotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(BuildshieldNotificationApplication.class, args);
    }
}
