package pe.buildshield.notification.acceptance;

import io.cucumber.java.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import pe.buildshield.notification.support.KafkaReadiness;
import pe.buildshield.notification.support.MutableClock;
import pe.buildshield.notification.support.TestDatabase;

import java.time.Instant;

/**
 * Antes de cada escenario: base vacía (tablas del servicio), reloj en la hora actual y consumidores Kafka con sus
 * particiones asignadas.
 */
public class DatabaseHooks {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    KafkaListenerEndpointRegistry listeners;

    @Before(order = 0)
    public void resetState() {
        TestDatabase.reset(jdbc);
        clock.setTo(Instant.now());
        KafkaReadiness.awaitAssignments(listeners);
    }
}
