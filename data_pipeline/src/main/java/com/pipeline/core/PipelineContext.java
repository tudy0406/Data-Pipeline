package com.pipeline.core;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class PipelineContext {

    private final String inputPath;
    private final Path outputDir;
    private final Map<String, Object> results;

    public PipelineContext(String inputPath, Path outputDir) {
        this.inputPath = inputPath;
        this.outputDir = outputDir;
        this.results = new HashMap<>();
    }

    public String getInputPath() {
        return inputPath;
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public void setResult(String key, Object value) {
        results.put(key, value);
    }

    public Object getResult(String key) {
        return results.get(key);
    }

    public boolean hasResult(String key) {
        return results.containsKey(key);
    }
}
