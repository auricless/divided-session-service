package org.c3cavite.sessionmanager.events;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EventRepository extends JpaRepository<EventEntity, UUID> {
  List<EventEntity> findAllByOrderByNameAscIdAsc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from SessionEvent e where e.id = :id")
  Optional<EventEntity> lockById(@Param("id") UUID id);
}
