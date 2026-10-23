package org.c3cavite.sessionmanager.api;

import java.util.List;
import org.c3cavite.core.domain.AssignmentResult;
import org.c3cavite.core.domain.Facilitator;
import org.c3cavite.core.domain.FacilitatorException;
import org.c3cavite.core.domain.Group;
import org.c3cavite.core.domain.Participant;
import org.c3cavite.core.domain.SessionConfig;
import org.springframework.stereotype.Component;

@Component
class SessionAssignmentMapper {
  private final SessionConfigMapper configMapper;

  SessionAssignmentMapper(SessionConfigMapper configMapper) {
    this.configMapper = configMapper;
  }

  AssignmentInput toDomain(AssignmentRequest request) {
    required(request, "request");
    SessionConfig config = configMapper.toDomain(request.config());
    List<Participant> participants =
        required(request.participants(), "participants").stream().map(this::participant).toList();
    List<Facilitator> facilitators =
        required(request.facilitators(), "facilitators").stream().map(this::facilitator).toList();
    List<FacilitatorException> exceptions =
        request.facilitatorExceptions() == null
            ? List.of()
            : request.facilitatorExceptions().stream()
                .map(input -> exception(input, facilitators))
                .toList();
    return new AssignmentInput(config, participants, facilitators, exceptions);
  }

  private Participant participant(AssignmentRequest.ParticipantInput input) {
    required(input, "participant entry");
    return new Participant(
        input.name(), required(input.age(), "participant age"), input.gender(), input.lifeStage());
  }

  private Facilitator facilitator(AssignmentRequest.FacilitatorInput input) {
    required(input, "facilitator entry");
    return new Facilitator(input.name(), input.role(), input.gender(), input.qualifiedTiers());
  }

  private FacilitatorException exception(
      AssignmentRequest.ExceptionInput input, List<Facilitator> facilitators) {
    required(input, "facilitator exception entry");
    int index = required(input.facilitatorIndex(), "facilitatorIndex");
    if (index < 0 || index >= facilitators.size()) {
      throw new IllegalArgumentException(
          "facilitatorIndex must select an entry in the facilitator roster");
    }
    return new FacilitatorException(
        facilitators.get(index),
        Boolean.TRUE.equals(input.allowAgeTierMismatch()),
        Boolean.TRUE.equals(input.allowGenderMismatch()));
  }

  AssignmentResponse toResponse(AssignmentResult result) {
    return new AssignmentResponse(
        result.getState(),
        result.getGroups().stream().map(this::group).toList(),
        result.getBlockers().stream()
            .map(
                blocker ->
                    new AssignmentResponse.BlockerOutput(
                        blocker.getCode(), blocker.getGroupNumber(), blocker.getMessage()))
            .toList(),
        result.getUnassignedParticipants().stream()
            .map(
                unassigned ->
                    new AssignmentResponse.UnassignedOutput(
                        participant(unassigned.participant()), unassigned.reason()))
            .toList(),
        result.getWarnings(),
        result.getTotalGroups(),
        result.getTotalAssignedParticipants());
  }

  private AssignmentResponse.GroupOutput group(Group group) {
    return new AssignmentResponse.GroupOutput(
        group.getGenderTarget(),
        group.getAgeTierTarget(),
        group.getLifeStageTarget(),
        group.getParticipants().stream().map(this::participant).toList(),
        facilitator(group.getLeader()),
        facilitator(group.getAssistant()),
        group.getSize());
  }

  private AssignmentResponse.ParticipantOutput participant(Participant participant) {
    return new AssignmentResponse.ParticipantOutput(
        participant.getName(),
        participant.getAge(),
        participant.getGender(),
        participant.getLifeStage(),
        participant.getAgeTier());
  }

  private AssignmentResponse.FacilitatorOutput facilitator(Facilitator facilitator) {
    return facilitator == null
        ? null
        : new AssignmentResponse.FacilitatorOutput(
            facilitator.getName(),
            facilitator.getRole(),
            facilitator.getGender(),
            facilitator.getQualifiedTiers());
  }

  private <T> T required(T value, String field) {
    if (value == null) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value;
  }

  record AssignmentInput(
      SessionConfig config,
      List<Participant> participants,
      List<Facilitator> facilitators,
      List<FacilitatorException> exceptions) {}
}
