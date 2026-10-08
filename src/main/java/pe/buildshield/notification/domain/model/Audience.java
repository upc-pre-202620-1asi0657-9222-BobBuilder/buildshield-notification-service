package pe.buildshield.notification.domain.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Destinatarios de un aviso: sin repetidos y sin quien provocó el evento (no se avisa a uno mismo de lo que acaba de
 * hacer). Conserva el orden en que se agregaron.
 */
public final class Audience {

    private final Map<UUID, Recipient> recipients = new LinkedHashMap<>();
    private final UUID actorId;

    public Audience(UUID actorId) {
        this.actorId = actorId;
    }

    public Audience add(Collection<Recipient> candidates) {
        candidates.forEach(this::add);
        return this;
    }

    public Audience add(Recipient candidate) {
        if (candidate != null && candidate.active() && !candidate.userId().equals(actorId)) {
            recipients.putIfAbsent(candidate.userId(), candidate);
        }
        return this;
    }

    public List<Recipient> recipients() {
        return new ArrayList<>(recipients.values());
    }

    public boolean isEmpty() {
        return recipients.isEmpty();
    }
}
