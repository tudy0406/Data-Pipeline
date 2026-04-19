package com.pipeline;

import com.pipeline.core.PipelineContext;
import com.pipeline.model.PipelinePhase;
import com.pipeline.tasks.analysis.CreditRoller;
import com.pipeline.tasks.analysis.IntroOutroDetector;
import com.pipeline.tasks.analysis.SceneIndexer;
import com.pipeline.tasks.audio_text.AIDubber;
import com.pipeline.tasks.audio_text.SpeechToText;
import com.pipeline.tasks.audio_text.Translator;
import com.pipeline.tasks.compliance.RegionalBranding;
import com.pipeline.tasks.compliance.SafetyScanner;
import com.pipeline.tasks.ingest.FormatValidator;
import com.pipeline.tasks.ingest.IntegrityCheck;
import com.pipeline.tasks.packaging.DRMWrapper;
import com.pipeline.tasks.packaging.ManifestBuilder;
import com.pipeline.tasks.visuals.SceneComplexity;
import com.pipeline.tasks.visuals.SpriteGenerator;
import com.pipeline.tasks.visuals.Transcoder;

import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: pipeline <input.mp4> <output_dir> [scripts_dir]");
            System.err.println("  input.mp4   — path to the master video file");
            System.err.println("  output_dir  — root folder for the output bundle (e.g. movie_101/)");
            System.err.println("  scripts_dir — optional path to the Python scripts folder (default: scripts/)");
            System.exit(1);
        }

        String inputPath  = args[0];
        Path   outputDir  = Path.of(args[1]);
        Path   scriptsDir = args.length >= 3 ? Path.of(args[2]) : Path.of("scripts");

        PipelineContext ctx = new PipelineContext(inputPath, outputDir, scriptsDir);

        Orchestrator orchestrator = new Orchestrator();
        registerTasks(orchestrator);

        System.out.println("=================================================");
        System.out.println("  Pipeline starting");
        System.out.println("  Input  : " + inputPath);
        System.out.println("  Output : " + outputDir);
        System.out.println("  Scripts: " + scriptsDir);
        System.out.println("=================================================");

        orchestrator.run(ctx);
    }

    private static void registerTasks(Orchestrator orchestrator) {

        // ── Phase 1: INGEST ──────────────────────────────────────────────────
        orchestrator.registerTask(PipelinePhase.INGEST, new IntegrityCheck());
        orchestrator.registerTask(PipelinePhase.INGEST, new FormatValidator());

        // ── Phase 2: ANALYSIS ────────────────────────────────────────────────
        orchestrator.registerTask(PipelinePhase.ANALYSIS, new IntroOutroDetector());
        orchestrator.registerTask(PipelinePhase.ANALYSIS, new CreditRoller());
        orchestrator.registerTask(PipelinePhase.ANALYSIS, new SceneIndexer(5));

        // ── Phase 3: VISUALS (parallel branch) ──────────────────────────────
        orchestrator.registerTask(PipelinePhase.VISUALS, new SceneComplexity());
        orchestrator.registerTask(PipelinePhase.VISUALS, new Transcoder());
        orchestrator.registerTask(PipelinePhase.VISUALS, new SpriteGenerator());

        // ── Phase 4: AUDIO_TEXT (parallel branch) ───────────────────────────
        orchestrator.registerTask(PipelinePhase.AUDIO_TEXT, new SpeechToText("base"));
        orchestrator.registerTask(PipelinePhase.AUDIO_TEXT, new Translator("RO"));
        orchestrator.registerTask(PipelinePhase.AUDIO_TEXT, new AIDubber("ro"));

        // ── Phase 5: COMPLIANCE ──────────────────────────────────────────────
        orchestrator.registerTask(PipelinePhase.COMPLIANCE, new SafetyScanner());
        orchestrator.registerTask(PipelinePhase.COMPLIANCE, new RegionalBranding());

        // ── Phase 6: PACKAGING ───────────────────────────────────────────────
        orchestrator.registerTask(PipelinePhase.PACKAGING, new DRMWrapper());
        orchestrator.registerTask(PipelinePhase.PACKAGING, new ManifestBuilder());
    }
}
