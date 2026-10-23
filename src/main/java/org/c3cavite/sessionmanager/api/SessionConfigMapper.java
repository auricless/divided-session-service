package org.c3cavite.sessionmanager.api;

import java.util.EnumMap;
import java.util.Map;
import org.c3cavite.core.domain.AgeRange;
import org.c3cavite.core.domain.AgeTier;
import org.c3cavite.core.domain.SessionConfig;
import org.springframework.stereotype.Component;

/** Shared transport-to-core configuration validation for stateless and stored events. */
@Component
public class SessionConfigMapper {
  public SessionConfig toDomain(AssignmentRequest.Config input) {
    required(input, "config");
    SessionConfig.Builder builder = SessionConfig.builder().capSize(input.capSize());
    if (input.targetSize() != null) {
      builder.targetSize(input.targetSize());
    }
    if (input.genderMode() != null) {
      builder.genderMode(input.genderMode());
    }
    if (input.activeTiers() != null) {
      builder.activeTiers(input.activeTiers());
    }
    if (input.lifeStageSubgroupingEnabled() != null) {
      builder.lifeStageSubgroupingEnabled(input.lifeStageSubgroupingEnabled());
    }
    if (input.ageTierBoundaries() != null) {
      Map<AgeTier, AgeRange> boundaries = new EnumMap<>(AgeTier.class);
      input
          .ageTierBoundaries()
          .forEach(
              (tier, range) -> {
                required(range, "ageTierBoundaries range");
                boundaries.put(
                    tier,
                    new AgeRange(
                        required(range.minAge(), "minAge"), required(range.maxAge(), "maxAge")));
              });
      builder.ageTierBoundaries(boundaries);
    }
    return builder.build();
  }

  public AssignmentRequest.Config toResponse(SessionConfig config) {
    Map<AgeTier, AssignmentRequest.RangeInput> ranges = new EnumMap<>(AgeTier.class);
    config
        .getAgeTierBoundaries()
        .forEach(
            (tier, range) ->
                ranges.put(tier, new AssignmentRequest.RangeInput(range.minAge(), range.maxAge())));
    return new AssignmentRequest.Config(
        config.getCapSize(),
        config.getTargetSize(),
        config.getGenderMode(),
        config.getActiveTiers(),
        config.isLifeStageSubgroupingEnabled(),
        ranges);
  }

  private <T> T required(T value, String field) {
    if (value == null) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value;
  }
}
