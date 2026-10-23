package org.c3cavite.sessionmanager.events;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ParticipantRepository extends JpaRepository<ParticipantEntity, UUID> {
  List<ParticipantEntity> findAllByEventIdOrderByNameAscIdAsc(UUID eventId);

  Optional<ParticipantEntity> findByIdAndEventId(UUID id, UUID eventId);
}
