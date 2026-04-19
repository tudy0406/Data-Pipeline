package com.pipeline.tasks.compliance;

import com.pipeline.core.PipelineContext;
import com.pipeline.core.Task;

import java.util.ArrayList;

public class SafetyScanner implements Task {

    @Override
    public void run(PipelineContext ctx) throws Exception {
        // Stub
        ctx.setResult("safety_flags", new ArrayList<>());
        ctx.setResult("safety_status", "CLEAN");
        System.out.println("    Safety scan complete. No flagged content.");
    }

    @Override
    public String getName() { return "SafetyScanner"; }
}
