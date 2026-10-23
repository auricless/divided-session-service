package org.c3cavite.sessionmanager.events;

import java.util.UUID;
import org.c3cavite.sessionmanager.api.AssignmentRequest;

public record EventResponse(UUID id, String name, AssignmentRequest.Config config, long revision) {}
