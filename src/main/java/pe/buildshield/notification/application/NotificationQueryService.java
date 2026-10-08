package pe.buildshield.notification.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.notification.domain.model.NotificationPage;
import pe.buildshield.notification.domain.model.NotificationRepository;
import pe.buildshield.notification.shared.tenant.TenantContext;

/** Bandeja del usuario del token: solo sus notificaciones. */
@Service
public class NotificationQueryService {

    public static final int MAX_SIZE = 100;

    private final NotificationRepository notifications;

    public NotificationQueryService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public Inbox inbox(boolean unreadOnly, int page, int size) {
        var userId = TenantContext.require().userId();
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? 20 : Math.min(size, MAX_SIZE);
        NotificationPage found = notifications.findByRecipient(userId, unreadOnly, safePage, safeSize);
        return new Inbox(found, notifications.countUnread(userId));
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notifications.countUnread(TenantContext.require().userId());
    }

    public record Inbox(NotificationPage page, long unreadCount) {
    }
}
