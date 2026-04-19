package com.pipeline.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

public class ProcessRunner {

    /** Run a command, merging stderr into stdout. */
    public static String run(List<String> command) throws Exception {
        return run(command, null);
    }

    /** Run a command with extra environment variables merged in. */
    public static String run(List<String> command, Map<String, String> extraEnv) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        if (extraEnv != null) {
            pb.environment().putAll(extraEnv);
        }

        Process process = pb.start();
        String output = collectOutput(process);

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception(
                "Command failed (exit " + exitCode + "): " + String.join(" ", command) +
                "\nOutput:\n" + output
            );
        }
        return output;
    }

    private static String collectOutput(Process process) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
