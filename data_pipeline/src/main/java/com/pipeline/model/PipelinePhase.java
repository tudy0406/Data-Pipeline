package com.pipeline.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public enum PipelinePhase {

    DONE,
    FAILED,
    PACKAGING(DONE),
    COMPLIANCE(PACKAGING),
    VISUALS(COMPLIANCE),
    AUDIO_TEXT(COMPLIANCE),
    ANALYSIS(VISUALS, AUDIO_TEXT),
    INGEST(ANALYSIS),
    IDLE(INGEST);

    private final Set<PipelinePhase> allowedNext;

    PipelinePhase(PipelinePhase... next) {
        // HashSet avoids the EnumSet bootstrapping issue where the class
        // is not yet registered as an enum during its own static initialisation.
        this.allowedNext = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(next)));
    }

    public boolean canTransitionTo(PipelinePhase next) {
        return allowedNext.contains(next);
    }

    public Set<PipelinePhase> getAllowedNext() {
        return allowedNext;
    }
}
