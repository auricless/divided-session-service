package org.c3cavite.sessionmanager.events;

import java.util.UUID;
import org.c3cavite.core.domain.AgeTier;
import org.c3cavite.core.domain.Gender;
import org.c3cavite.core.domain.LifeStage;

public record ParticipantResponse(
    UUID id, String name, int age, Gender gender, LifeStage lifeStage, AgeTier ageTier) {}
