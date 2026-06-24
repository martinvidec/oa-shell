package dev.oashell.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelSessionRepository extends JpaRepository<ChannelSession, Long> {

    List<ChannelSession> findByUserId(Long userId);

    Optional<ChannelSession> findByConnectionId(String connectionId);
}
