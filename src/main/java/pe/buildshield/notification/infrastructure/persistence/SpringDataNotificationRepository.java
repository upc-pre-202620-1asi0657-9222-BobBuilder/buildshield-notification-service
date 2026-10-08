package pe.buildshield.notification.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

interface SpringDataNotificationRepository extends JpaRepository<NotificationJpaEntity, UUID> {

    Page<NotificationJpaEntity> findByRecipientUserId(UUID recipientUserId, Pageable pageable);

    Page<NotificationJpaEntity> findByRecipientUserIdAndReadAtIsNull(UUID recipientUserId, Pageable pageable);

    long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE NotificationJpaEntity n SET n.readAt = :now
            WHERE n.recipientUserId = :recipient AND n.readAt IS NULL""")
    int markAllRead(@Param("recipient") UUID recipientUserId, @Param("now") Instant now);
}
