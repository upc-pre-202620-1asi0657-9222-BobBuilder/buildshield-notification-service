package pe.buildshield.notification.application;

import pe.buildshield.notification.domain.model.DispatchInfo;
import pe.buildshield.notification.domain.model.LogisticsDirectory;
import pe.buildshield.notification.domain.model.Notification;
import pe.buildshield.notification.domain.model.NotificationPage;
import pe.buildshield.notification.domain.model.NotificationRepository;
import pe.buildshield.notification.domain.model.Recipient;
import pe.buildshield.notification.domain.model.RecipientDirectory;
import pe.buildshield.notification.shared.tenant.TenantContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Bandeja y copias locales en memoria, con el filtro por organización de la persistencia real. */
class InMemoryNotifications implements NotificationRepository, RecipientDirectory, LogisticsDirectory {

    final List<Notification> notifications = new ArrayList<>();
    final Map<UUID, Recipient> users = new HashMap<>();
    final Map<UUID, UUID> userOrganization = new HashMap<>();
    final Map<UUID, UUID[]> assignments = new HashMap<>();
    final Map<UUID, String> locations = new HashMap<>();
    final Map<UUID, String> locationTypes = new HashMap<>();
    final Map<UUID, DispatchInfo> dispatches = new HashMap<>();

    private UUID organization() {
        return TenantContext.require().organizationId();
    }

    @Override
    public void saveAll(List<Notification> list) {
        notifications.addAll(list);
    }

    @Override
    public Notification save(Notification notification) {
        notifications.removeIf(existing -> existing.id().equals(notification.id()));
        notifications.add(notification);
        return notification;
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return notifications.stream().filter(n -> n.id().equals(id) && n.organizationId().equals(organization()))
                .findFirst();
    }

    @Override
    public NotificationPage findByRecipient(UUID recipientUserId, boolean unreadOnly, int page, int size) {
        List<Notification> found = notifications.stream()
                .filter(n -> n.organizationId().equals(organization()) && n.recipientUserId().equals(recipientUserId))
                .filter(n -> !unreadOnly || !n.isRead())
                .sorted(Comparator.comparing(Notification::createdAt).reversed())
                .toList();
        return new NotificationPage(found.stream().skip((long) page * size).limit(size).toList(), page, size,
                found.size());
    }

    @Override
    public long countUnread(UUID recipientUserId) {
        return findByRecipient(recipientUserId, true, 0, Integer.MAX_VALUE).total();
    }

    @Override
    public int markAllRead(UUID recipientUserId, Instant now) {
        int count = 0;
        for (Notification n : findByRecipient(recipientUserId, true, 0, Integer.MAX_VALUE).items()) {
            n.markRead(now);
            count++;
        }
        return count;
    }

    @Override
    public void upsertUser(UUID organizationId, UUID userId, String fullName, String email, String role,
            boolean active) {
        users.put(userId, new Recipient(userId, fullName, email, role, active));
        userOrganization.put(userId, organizationId);
    }

    @Override
    public void assign(UUID organizationId, UUID assignmentId, UUID userId, UUID locationId, String locationType) {
        assignments.put(assignmentId, new UUID[]{userId, locationId});
    }

    @Override
    public void unassign(UUID assignmentId) {
        assignments.remove(assignmentId);
    }

    @Override
    public Optional<Recipient> find(UUID organizationId, UUID userId) {
        return Optional.ofNullable(users.get(userId)).filter(r -> organizationId.equals(userOrganization.get(userId)));
    }

    @Override
    public List<Recipient> assignedTo(UUID organizationId, UUID locationId, String role) {
        return assignments.values().stream().filter(a -> a[1].equals(locationId)).map(a -> users.get(a[0]))
                .filter(r -> r != null && r.active() && r.role().equals(role)).toList();
    }

    @Override
    public List<Recipient> withRole(UUID organizationId, String role) {
        return users.values().stream().filter(r -> r.active() && r.role().equals(role)
                && organizationId.equals(userOrganization.get(r.userId()))).toList();
    }

    @Override
    public void upsertLocation(UUID organizationId, UUID locationId, String type, String name) {
        locations.put(locationId, name);
        locationTypes.put(locationId, type);
    }

    @Override
    public boolean isWorksite(UUID organizationId, UUID locationId) {
        return "WORKSITE".equals(locationTypes.get(locationId));
    }

    @Override
    public List<Recipient> candidates(UUID organizationId, UUID worksiteId) {
        return users.values().stream()
                .filter(r -> r.active() && organizationId.equals(userOrganization.get(r.userId())))
                .filter(r -> !Recipient.SITE_MANAGER.equals(r.role()) || assignments.values().stream()
                        .anyMatch(a -> a[0].equals(r.userId()) && a[1].equals(worksiteId)))
                .sorted(java.util.Comparator.comparing(Recipient::fullName))
                .toList();
    }

    @Override
    public Optional<String> locationName(UUID organizationId, UUID locationId) {
        return Optional.ofNullable(locations.get(locationId));
    }

    @Override
    public void upsertDispatch(UUID organizationId, UUID dispatchId, String manifestCode, UUID worksiteId,
            UUID warehouseId) {
        DispatchInfo known = dispatches.get(dispatchId);
        dispatches.put(dispatchId, new DispatchInfo(dispatchId,
                manifestCode != null ? manifestCode : known == null ? null : known.manifestCode(),
                worksiteId != null ? worksiteId : known == null ? null : known.worksiteId(),
                warehouseId != null ? warehouseId : known == null ? null : known.warehouseId()));
    }

    @Override
    public Optional<DispatchInfo> dispatch(UUID organizationId, UUID dispatchId) {
        return Optional.ofNullable(dispatches.get(dispatchId));
    }
}
