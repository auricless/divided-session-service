package org.c3cavite.sessionmanager.events;

import org.c3cavite.sessionmanager.api.AssignmentRequest;

public record EventRequest(String name, AssignmentRequest.Config config) {}
