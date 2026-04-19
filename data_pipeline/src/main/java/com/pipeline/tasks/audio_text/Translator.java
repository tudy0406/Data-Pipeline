package com.pipeline.tasks.audio_text;

import com.pipeline.core.PipelineContext;
import com.pipeline.core.Task;
import com.pipeline.util.ProcessRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Translator implements Task {

    private final String targetLang;

    public Translator() {
        this("RO");
    }

    public Translator(String targetLang) {
        this.targetLang = targetLang;
    }

    @Override
    public void run(PipelineContext ctx) throws Exception {
        Path textDir = ctx.getOutputDir().resolve("text");
        Files.createDirectories(textDir);

        // Resolve input — use the transcript written by SpeechToText
        String transcriptPath = ctx.hasResult("transcript_path")
            ? (String) ctx.getResult("transcript_path")
            : textDir.resolve("source_transcript.txt").toString();

        String outputFileName = targetLang.toLowerCase().split("-")[0] + "_translation.txt";
        Path translationFile = textDir.resolve(outputFileName);
        String script = ctx.getScriptsDir().resolve("translate.py").toString();

        // Pass the DeepL API key from the environment
        String apiKey = System.getenv("DEEPL_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new Exception("DEEPL_API_KEY environment variable is not set.");
        }

        System.out.println("    Translating to '" + targetLang + "' via DeepL...");
        ProcessRunner.run(
            List.of(
                "python", script,
                "--input",       transcriptPath,
                "--target-lang", targetLang,
                "--output",      translationFile.toString()
            ),
            Map.of("DEEPL_API_KEY", apiKey)
        );

        ctx.setResult("ro_translation_path", translationFile.toString());
        System.out.println("    Translation written to " + outputFileName);
    }

    @Override
    public String getName() { return "Translator"; }
}
