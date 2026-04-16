package com.pipeline.core;

public interface Task {
    void run(PipelineContext ctx) throws Exception;
    String getName();
}
