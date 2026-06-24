package dev.oashell.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Eine gekoppelte Channel-Session eines Nutzers (Model A: läuft lokal beim Nutzer,
 * verbindet sich ausgehend zur Bridge). Ein Nutzer kann mehrere Sessions haben.
 */
@Entity
@Table(name = "channel_session")
public class ChannelSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "cwd_basename")
    private String cwdBasename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(name = "connection_id", nullable = false)
    private String connectionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    protected ChannelSession() {
        // für JPA
    }

    public ChannelSession(Long userId, String connectionId) {
        this.userId = userId;
        this.connectionId = connectionId;
        this.status = SessionStatus.CONNECTED;
        this.createdAt = Instant.now();
        this.lastSeenAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getCwdBasename() {
        return cwdBasename;
    }

    public void setCwdBasename(String cwdBasename) {
        this.cwdBasename = cwdBasename;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
