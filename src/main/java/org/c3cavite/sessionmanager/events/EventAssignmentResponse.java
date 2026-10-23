package org.c3cavite.sessionmanager.events;

import java.util.UUID;
import org.c3cavite.sessionmanager.api.AssignmentResponse;

public record EventAssignmentResponse(
    UUID eventId, String eventName, long revision, AssignmentResponse result) {}
