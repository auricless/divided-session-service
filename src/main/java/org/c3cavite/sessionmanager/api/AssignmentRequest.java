package org.c3cavite.sessionmanager.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.c3cavite.core.domain.AgeTier;
import org.c3cavite.core.domain.Gender;
import org.c3cavite.core.domain.GenderMode;
import org.c3cavite.core.domain.LifeStage;
import org.c3cavite.core.domain.Role;

public record AssignmentRequest(
    Config config,
    List<ParticipantInput> participants,
    List<FacilitatorInput> facilitators,
    List<ExceptionInput> facilitatorExceptions) {
  // Preserve nulls so the mapper can report boundary validation as a clean client error.
  public AssignmentRequest {
    participants =
        participants == null ? null : Collections.unmodifiableList(new ArrayList<>(participants));
    facilitators =
        facilitators == null ? null : Collections.unmodifiableList(new ArrayList<>(facilitators));
    facilitatorExceptions =
        facilitatorExceptions == null
            ? null
            : Collections.unmodifiableList(new ArrayList<>(facilitatorExceptions));
  }

  public record Config(
      Integer capSize,
      Integer targetSize,
      GenderMode genderMode,
      List<AgeTier> activeTiers,
      Boolean lifeStageSubgroupingEnabled,
      Map<AgeTier, RangeInput> ageTierBoundaries) {
    public Config {
      activeTiers =
          activeTiers == null ? null : Collections.unmodifiableList(new ArrayList<>(activeTiers));
      ageTierBoundaries =
          ageTierBoundaries == null
              ? null
              : Collections.unmodifiableMap(new LinkedHashMap<>(ageTierBoundaries));
    }
  }

  public record RangeInput(Integer minAge, Integer maxAge) {}

  public record ParticipantInput(String name, Integer age, Gender gender, LifeStage lifeStage) {}

  public record FacilitatorInput(
      String name, Role role, Gender gender, List<AgeTier> qualifiedTiers) {
    public FacilitatorInput {
      qualifiedTiers =
          qualifiedTiers == null
              ? null
              : Collections.unmodifiableList(new ArrayList<>(qualifiedTiers));
    }
  }

  public record ExceptionInput(
      Integer facilitatorIndex, Boolean allowAgeTierMismatch, Boolean allowGenderMismatch) {}
}
