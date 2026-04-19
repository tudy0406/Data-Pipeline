package com.pipeline.tasks.audio_text;

import com.pipeline.core.PipelineContext;
import com.pipeline.core.Task;
import com.pipeline.util.ProcessRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SpeechToText implements Task {

    private final String whisperModel;

    public SpeechToText() {
        this("base");
    }

    public SpeechToText(String whisperModel) {
        this.whisperModel = whisperModel;
    }

    @Override
    public void run(PipelineContext ctx) throws Exception {
        Path textDir = ctx.getOutputDir().resolve("text");
        Path audioDir = ctx.getOutputDir().resolve("audio");
        Files.createDirectories(textDir);
        Files.createDirectories(audioDir);

        Path transcriptFile = textDir.resolve("source_transcript.txt");
        String script = ctx.getScriptsDir().resolve("stt.py").toString();

        System.out.println("    Running Whisper model '" + whisperModel + "'...");
        ProcessRunner.run(List.of(
            "python", script,
            "--input",  ctx.getInputPath(),
            "--output", transcriptFile.toString(),
            "--model",  whisperModel
        ));

        // Read the transcript back into context so Translator can use it
        String transcript = Files.readString(transcriptFile);
        ctx.setResult("transcript_path", transcriptFile.toString());
        ctx.setResult("transcript_text", transcript);
        System.out.println("    Transcript written to source_transcript.txt");
    }

    @Override
    public String getName() { return "SpeechToText"; }
}
