package com.pipeline.tasks.audio_text;

import com.pipeline.core.PipelineContext;
import com.pipeline.core.Task;
import com.pipeline.util.ProcessRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class AIDubber implements Task {

    private final String lang;

    public AIDubber() {
        this("ro");
    }

    // lang should be the BCP-47 code matching the translation (e.g. "ro", "de", "fr")
    public AIDubber(String lang) {
        this.lang = lang;
    }

    @Override
    public void run(PipelineContext ctx) throws Exception {
        Path audioDir = ctx.getOutputDir().resolve("audio");
        Files.createDirectories(audioDir);

        // Use the translation file produced by Translator
        String translationPath = ctx.hasResult("ro_translation_path")
            ? (String) ctx.getResult("ro_translation_path")
            : ctx.getOutputDir().resolve("text").resolve("ro_translation.txt").toString();

        Path dubFile = audioDir.resolve("ro_dub_synthetic.aac");
        String script = ctx.getScriptsDir().resolve("tts.py").toString();

        System.out.println("    Generating synthetic dub (lang: " + lang + ")...");
        ProcessRunner.run(List.of(
            "python", script,
            "--input",  translationPath,
            "--lang",   lang,
            "--output", dubFile.toString()
        ));

        ctx.setResult("ro_dub_path", dubFile.toString());
        System.out.println("    Synthetic dub written to ro_dub_synthetic.aac");
    }

    @Override
    public String getName() { return "AIDubber"; }
}
