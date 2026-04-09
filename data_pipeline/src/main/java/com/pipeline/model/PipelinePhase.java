package com.pipeline.model;

import java.util.EnumSet;
import java.util.Set;


public enum PipelinePhase {

    DONE(EnumSet.noneOf(PipelinePhase.class)),
    FAILED(EnumSet.noneOf(PipelinePhase.class)),
    PACKAGING(EnumSet.of(DONE)),
    COMPLIANCE(EnumSet.of(PACKAGING)),
    VISUALS(EnumSet.of(COMPLIANCE)),
    AUDIO_TEXT(EnumSet.of(COMPLIANCE)),
    ANALYSIS(EnumSet.of(VISUALS, AUDIO_TEXT)),
    INGEST(EnumSet.of(ANALYSIS)),
    IDLE(EnumSet.of(INGEST));
    
    private final Set<PipelinePhase> allowedNext;

    PipelinePhase(Set<PipelinePhase> allowedNext) {
        this.allowedNext = allowedNext;
    }

    public boolean canTransitionTo(PipelinePhase next) {
        return allowedNext.contains(next);
    }

    public Set<PipelinePhase> getAllowedNext() {
        return allowedNext;
    }
}