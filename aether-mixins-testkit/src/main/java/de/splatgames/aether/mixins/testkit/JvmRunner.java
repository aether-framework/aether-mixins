package de.splatgames.aether.mixins.testkit;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class JvmRunner {
    public static final class Result {
        public final String stdout;
        public final String stderr;
        public final int exitCode;

        Result(final String out, final String err, final int code) {
            this.stdout = out;
            this.stderr = err;
            this.exitCode = code;
        }
    }

    public static Result runWithAgent(@NotNull final String mainClass, final List<String> args, final Map<String,String> sysProps) throws Exception {
        String java = Paths.get(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        String cp = System.getProperty("java.class.path");
        String agent = System.getProperty("agent.jar");
        if (agent == null) agent = System.getenv("agent.jar");
        if (agent == null || agent.isBlank()) throw new IllegalStateException("agent.jar not set");

        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(java);
        cmd.add("-javaagent:" + agent);
        if (sysProps != null) {
            for (var e : sysProps.entrySet()) cmd.add("-D" + e.getKey() + "=" + e.getValue());
        }
        cmd.add("-cp"); cmd.add(cp);
        cmd.add(mainClass);
        cmd.addAll(args);

        var p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        return new Result(out, err, code);
    }


    public static Result runWithAgentInDir(String mainClass,
                                           List<String> args,
                                           Map<String, String> props,
                                           File workingDir) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        String agentJar = System.getProperty("agent.jar");

        if (agentJar == null || agentJar.isBlank()) {
            throw new IllegalStateException("System property 'agent.jar' must be set for integration tests");
        }

        cmd.add("-javaagent:" + agentJar);
        for (var e : props.entrySet()) {
            cmd.add("-D" + e.getKey() + "=" + e.getValue());
        }

        cmd.add(mainClass);
        cmd.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workingDir);
        pb.redirectErrorStream(false);

        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes());
        String stderr = new String(process.getErrorStream().readAllBytes());
        int exitCode = process.waitFor();

        return new Result(stdout, stderr, exitCode);
    }
}
