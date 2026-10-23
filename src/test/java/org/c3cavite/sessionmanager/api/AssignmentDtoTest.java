package org.c3cavite.sessionmanager.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.c3cavite.core.domain.AgeTier;
import org.c3cavite.core.domain.AssignmentState;
import org.c3cavite.core.domain.Gender;
import org.c3cavite.core.domain.Role;
import org.junit.jupiter.api.Test;

class AssignmentDtoTest {
  @Test
  void requestCollectionsAreSnapshotsOfCallerInputs() {
    var tiers = new ArrayList<>(List.of(AgeTier.YOUTH));
    var boundaries = new HashMap<>(Map.of(AgeTier.YOUTH, new AssignmentRequest.RangeInput(16, 22)));
    var config = new AssignmentRequest.Config(6, null, null, tiers, null, boundaries);
    var qualifications = new ArrayList<>(List.of(AgeTier.YOUTH));
    var leader =
        new AssignmentRequest.FacilitatorInput("Sam", Role.LEADER, Gender.MALE, qualifications);
    var attendees =
        new ArrayList<>(
            List.of(new AssignmentRequest.ParticipantInput("Alex", 18, Gender.MALE, null)));
    var staff = new ArrayList<>(List.of(leader));
    var exceptions = new ArrayList<>(List.of(new AssignmentRequest.ExceptionInput(0, true, false)));
    var request = new AssignmentRequest(config, attendees, staff, exceptions);

    tiers.clear();
    boundaries.clear();
    qualifications.clear();
    attendees.clear();
    staff.clear();
    exceptions.clear();

    assertAll(
        () -> assertEquals(List.of(AgeTier.YOUTH), request.config().activeTiers()),
        () -> assertEquals(1, request.config().ageTierBoundaries().size()),
        () -> assertEquals(List.of(AgeTier.YOUTH), request.facilitators().get(0).qualifiedTiers()),
        () -> assertEquals(1, request.participants().size()),
        () -> assertEquals(1, request.facilitatorExceptions().size()),
        () ->
            assertThrows(UnsupportedOperationException.class, () -> request.participants().clear()),
        () ->
            assertThrows(UnsupportedOperationException.class, () -> request.facilitators().clear()),
        () ->
            assertThrows(
                UnsupportedOperationException.class, () -> request.facilitatorExceptions().clear()),
        () -> assertThrows(UnsupportedOperationException.class, () -> config.activeTiers().clear()),
        () ->
            assertThrows(
                UnsupportedOperationException.class, () -> config.ageTierBoundaries().clear()),
        () ->
            assertThrows(
                UnsupportedOperationException.class, () -> leader.qualifiedTiers().clear()));
  }

  @Test
  void responseCollectionsAreSnapshotsOfCallerInputs() {
    var qualifications = new ArrayList<>(List.of(AgeTier.YOUTH));
    var leader =
        new AssignmentResponse.FacilitatorOutput("Sam", Role.LEADER, Gender.MALE, qualifications);
    var attendees =
        new ArrayList<>(
            List.of(
                new AssignmentResponse.ParticipantOutput(
                    "Alex", 18, Gender.MALE, null, AgeTier.YOUTH)));
    var group =
        new AssignmentResponse.GroupOutput(
            Gender.MALE, AgeTier.YOUTH, null, attendees, leader, null, 1);
    var groups = new ArrayList<>(List.of(group));
    var blockers = new ArrayList<AssignmentResponse.BlockerOutput>();
    var unassigned = new ArrayList<AssignmentResponse.UnassignedOutput>();
    var warnings = new ArrayList<>(List.of("Review"));
    var response =
        new AssignmentResponse(AssignmentState.FINAL, groups, blockers, unassigned, warnings, 1, 1);

    qualifications.clear();
    attendees.clear();
    groups.clear();
    warnings.clear();

    assertAll(
        () -> assertEquals(1, response.groups().size()),
        () -> assertEquals(1, group.participants().size()),
        () -> assertEquals(List.of(AgeTier.YOUTH), leader.qualifiedTiers()),
        () -> assertEquals(List.of("Review"), response.warnings()),
        () -> assertThrows(UnsupportedOperationException.class, () -> response.groups().clear()),
        () -> assertThrows(UnsupportedOperationException.class, () -> response.blockers().clear()),
        () ->
            assertThrows(
                UnsupportedOperationException.class,
                () -> response.unassignedParticipants().clear()),
        () -> assertThrows(UnsupportedOperationException.class, () -> response.warnings().clear()),
        () -> assertThrows(UnsupportedOperationException.class, () -> group.participants().clear()),
        () ->
            assertThrows(
                UnsupportedOperationException.class, () -> leader.qualifiedTiers().clear()));
  }

  @Test
  void requestCopiesPreserveNullsForBoundaryValidation() {
    var nullEntries = new ArrayList<AssignmentRequest.ParticipantInput>();
    nullEntries.add(null);
    var request = new AssignmentRequest(null, nullEntries, null, null);
    var nullRanges = new HashMap<AgeTier, AssignmentRequest.RangeInput>();
    nullRanges.put(AgeTier.YOUTH, null);
    var config = new AssignmentRequest.Config(null, null, null, null, null, nullRanges);

    assertNull(request.config());
    assertNull(request.participants().get(0));
    assertNull(request.facilitators());
    assertNull(request.facilitatorExceptions());
    assertNull(config.activeTiers());
    assertNull(config.ageTierBoundaries().get(AgeTier.YOUTH));
  }
}
