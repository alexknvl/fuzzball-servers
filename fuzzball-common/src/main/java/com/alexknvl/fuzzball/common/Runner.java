package com.alexknvl.fuzzball.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import jwp.fuzz.BranchHit;
import jwp.fuzz.Tracer;
import tracehash.TraceHash;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Runner {
    protected interface CompilerContext {
        List<Message> getMessages();
        String getPhaseName();
    }

    protected final static class Message {
        private final String level;
        private final String message;
        private final String position;
        private final String phase;

        public Message(String level, String message, String position, String phase) {
            this.level = level;
            this.message = message;
            this.position = position;
            this.phase = phase;
        }
    }

    protected abstract CompilerContext newContext(String compilerArgs);
    protected abstract void compileSource(CompilerContext state, String source);

    private final class RunnerThread implements Runnable {
        private final String source;
        public final CompilerContext context;

        public RunnerThread(String source, String compilerArgs) {
            this.source = source;
            this.context = newContext(compilerArgs);
        }

        @Override public void run() {
            compileSource(context, source);
        }
    }

    private final class ExcSignature {
        String className;
        String message;
        String hash;
        StackTraceElement[] principal;

        ExcSignature cause;

        public ExcSignature(Throwable e) {
            hash = TraceHash.hash(TraceHash.DEFAULT_PARAMETERS, e);
            principal = TraceHash.principal(TraceHash.DEFAULT_PARAMETERS, e);

            message = e.getMessage();
            className = e.getClass().getName();

            // We assume that there are no loops, so this recursion is well-behaved.
            if (e.getCause() != null)
                cause = new ExcSignature(e.getCause());
        }
    }

    private Gson GSON = new GsonBuilder().create();

    private final class FinalOutput {
        String stdOut;
        String stdErr;

        Map<Integer, Integer> hits = new HashMap<>();

        List<Message> messages = new ArrayList<>();

        long time;

        ExcSignature exception;
        String lastPhase;
    }

    private void submitSource(String source, String compilerArgs, long timeout) throws InterruptedException {
        // This is not side-effecting.
        Tracer coverageTracer = new Tracer.Instrumenting();
        OutputTracer outputTracer = new OutputTracer();
        FinalOutput finalOutput = new FinalOutput();

        // Create a new compiler context and a runner.
        RunnerThread runnerThread = new RunnerThread(source, compilerArgs);
        Thread newThread = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    runnerThread.run();
                } catch (Throwable e) {
                    finalOutput.exception = new ExcSignature(e);
                    finalOutput.lastPhase = runnerThread.context.getPhaseName();
                }
            }
        });
        newThread.setUncaughtExceptionHandler((t, e) -> {
            finalOutput.exception = new ExcSignature(e);
            finalOutput.lastPhase = runnerThread.context.getPhaseName();
        });

        outputTracer.startTrace();
        coverageTracer.startTrace(newThread);
        long startTime = System.currentTimeMillis();
        newThread.start();
        newThread.join(timeout);
        long endTime = System.currentTimeMillis();

        BranchHit[] hits = coverageTracer.stopTrace(newThread);
        OutputTracer.Result output = outputTracer.stopTrace();

        if (newThread.isAlive()) {
            System.err.println("Force stopping the compiler thread.");
            newThread.stop();
            newThread.join(0);
        }

        finalOutput.stdOut = output.outBuf;
        finalOutput.stdErr = output.errBuf;
        finalOutput.messages = runnerThread.context.getMessages();
        finalOutput.time = endTime - startTime;

        for (BranchHit bh : hits) {
            finalOutput.hits.put(bh.branchHash, bh.hitCount);
        }

        System.out.println(GSON.toJson(finalOutput));
    }

    public void run(String[] args) throws Exception {
        OptionParser parser = new OptionParser();
        OptionSpec<Double> timeoutOpt = parser.accepts("timeout").withRequiredArg().ofType(Double.class).defaultsTo(30.0);
        OptionSpec<String> argsOpt = parser.accepts("args").withRequiredArg().defaultsTo(
                "-usejavacp -Ycheck:all -Ycheck-mods " +
                "-Ydebug -Ydebug-missing-refs " +
                "-Ydump-sbt-inc -Yforce-sbt-phases " +
                "-Xverify-signatures");
        OptionSet optionSet = parser.parse(args);

        long timeout = (long) (optionSet.valueOf(timeoutOpt) * 1000);
        if (timeout < 0) {
            System.err.println("Invalid timeout value.");
            return;
        }

        String compilerArgs = optionSet.valueOf(argsOpt);

        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        String line;
        while (true) {
            line = reader.readLine();
            if (line == null) {
                submitSource(builder.toString(), compilerArgs, timeout);
                break;
            }

            while (line.contains("\1")) {
                int i = line.indexOf('\1');
                builder.append(line, 0, i);
                String source = builder.toString();

                // Submit the source.
                submitSource(source, compilerArgs, timeout);

                // Reset the buffer.
                builder.setLength(0);
                line = line.substring(i + 1);
            }
            builder.append(line).append('\n');
        }
    }
}