package pe.buildshield.notification.domain.model;

import java.util.List;

/** Página de la bandeja, de la más reciente a la más antigua. */
public record NotificationPage(List<Notification> items, int page, int size, long total) {
}
