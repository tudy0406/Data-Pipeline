package com.pipeline;

import com.pipeline.core.PipelineContext;
import com.pipeline.core.Task;
import com.pipeline.model.PipelinePhase;
import com.pipeline.model.PhaseStatus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Orchestrator {

    private PipelinePhase currentPhase = PipelinePhase.IDLE;
    private final Map<PipelinePhase, PhaseStatus> phaseStatuses = new EnumMap<>(PipelinePhase.class);
    private final Map<PipelinePhase, List<Task>> phaseTasks = new EnumMap<>(PipelinePhase.class);

    public Orchestrator() {
        for (PipelinePhase phase : PipelinePhase.values()) {
            phaseStatuses.put(phase, PhaseStatus.PENDING);
            phaseTasks.put(phase, new ArrayList<>());
        }
    }

    public void registerTask(PipelinePhase phase, Task task) {
        phaseTasks.get(phase).add(task);
    }

    public void run(PipelineContext ctx) {
        try {
            transition(PipelinePhase.INGEST);
            runPhase(PipelinePhase.INGEST, ctx);

            transition(PipelinePhase.ANALYSIS);
            runPhase(PipelinePhase.ANALYSIS, ctx);

            //VISUALS and AUDIO_TEXT run in parallel
            runParallel(ctx, PipelinePhase.VISUALS, PipelinePhase.AUDIO_TEXT);

            transition(PipelinePhase.COMPLIANCE);
            runPhase(PipelinePhase.COMPLIANCE, ctx);

            transition(PipelinePhase.PACKAGING);
            runPhase(PipelinePhase.PACKAGING, ctx);

            transition(PipelinePhase.DONE);
            System.out.println("Pipeline completed successfully.");

        } catch (Exception e) {
            currentPhase = PipelinePhase.FAILED;
            phaseStatuses.put(currentPhase, PhaseStatus.FAILED);
            System.err.println("Pipeline failed at phase [" + currentPhase + "]: " + e.getMessage());
        }
    }

    private void runPhase(PipelinePhase phase, PipelineContext ctx) throws Exception {
        phaseStatuses.put(phase, PhaseStatus.RUNNING);
        System.out.println("Running phase: " + phase);

        for (Task task : phaseTasks.get(phase)) {
            System.out.println("  Running task: " + task.getName());
            task.run(ctx);
            System.out.println("  Completed task: " + task.getName());
        }

        phaseStatuses.put(phase, PhaseStatus.DONE);
        System.out.println("Completed phase: " + phase);
    }

    private void runParallel(PipelineContext ctx, PipelinePhase... phases) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(phases.length);
        List<Future<?>> futures = new ArrayList<>();

        // Validate all transitions before launching any thread
        for (PipelinePhase phase : phases) {
            if (!currentPhase.canTransitionTo(phase)) {
                throw new IllegalStateException("Invalid transition: " + currentPhase + " -> " + phase);
            }
        }
        currentPhase = phases[phases.length - 1];

        for (PipelinePhase phase : phases) {
            futures.add(executor.submit(() -> {
                try {
                    runPhase(phase, ctx);
                } catch (Exception e) {
                    throw new RuntimeException("Phase " + phase + " failed: " + e.getMessage(), e);
                }
            }));
        }

        executor.shutdown();

        for (Future<?> future : futures) {
            future.get(); //blocks until both VISUALS and AUDIO_TEXT are done
        }
    }

    private void transition(PipelinePhase next) {
        if (!currentPhase.canTransitionTo(next)) {
            throw new IllegalStateException(
                "Invalid transition: " + currentPhase + " -> " + next
            );
        }
        System.out.println("Transitioning: " + currentPhase + " -> " + next);
        currentPhase = next;
    }

    public PipelinePhase getCurrentPhase() {
        return currentPhase;
    }

    public PhaseStatus getPhaseStatus(PipelinePhase phase) {
        return phaseStatuses.get(phase);
    }
}