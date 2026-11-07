package pe.buildshield.notification.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.buildshield.notification.domain.model.NotificationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataNotificationRepository extends JpaRepository<NotificationJpaEntity, UUID> {

    List<NotificationJpaEntity> findByStatus(NotificationStatus status);

    Page<NotificationJpaEntity> findByUserId(UUID recipientUserId, Pageable pageable);

    Page<NotificationJpaEntity> findByUserIdAndReadAtIsNull(UUID recipientUserId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID recipientUserId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE NotificationJpaEntity n SET n.readAt = :now
            WHERE n.userId = :recipient AND n.readAt IS NULL""")
    int markAllRead(@Param("recipient") UUID recipientUserId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE NotificationJpaEntity n SET n.status = :status, n.attempts = :attempts, n.sentAt = :sentAt
            WHERE n.id = :id""")
    int recordDelivery(@Param("id") UUID id, @Param("status") NotificationStatus status,
            @Param("attempts") int attempts, @Param("sentAt") Instant sentAt);
}
