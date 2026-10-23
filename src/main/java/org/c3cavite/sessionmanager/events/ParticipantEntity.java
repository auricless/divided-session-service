package org.c3cavite.sessionmanager.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.c3cavite.core.domain.Gender;
import org.c3cavite.core.domain.LifeStage;
import org.c3cavite.core.domain.Participant;
import org.c3cavite.sessionmanager.api.AssignmentRequest;

@Entity
@Table(name = "event_participants")
class ParticipantEntity {
  @Id private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false)
  private int age;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 6)
  private Gender gender;

  @Enumerated(EnumType.STRING)
  @Column(name = "life_stage", length = 32)
  private LifeStage lifeStage;

  protected ParticipantEntity() {}

  ParticipantEntity(UUID eventId, Participant participant) {
    this.id = UUID.randomUUID();
    this.eventId = eventId;
    update(participant);
  }

  void update(Participant participant) {
    this.name = participant.getName();
    this.age = participant.getAge();
    this.gender = participant.getGender();
    this.lifeStage = participant.getLifeStage();
  }

  UUID id() {
    return id;
  }

  Participant toDomain() {
    return new Participant(name, age, gender, lifeStage);
  }

  AssignmentRequest.ParticipantInput toInput() {
    return new AssignmentRequest.ParticipantInput(name, age, gender, lifeStage);
  }
}
