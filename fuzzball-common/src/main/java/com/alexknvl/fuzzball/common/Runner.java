package com.alexknvl.fuzzball.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import jwp.fuzz.BranchHit;
import jwp.fuzz.Tracer;
import tracehash.TraceHash;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public abstract class Runner {
    protected interface GlobalContext {
        RunContext newRun();
    }

    protected abstract GlobalContext newContext(String compilerArgs);

    protected interface RunContext {
        void compileSource(String source);
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

    private final class RunnerThread implements Runnable {
        private final String source;
        public final RunContext ctx;

        public RunnerThread(String source, RunContext ctx) {
            this.source = source;
            this.ctx = ctx;
        }

        @Override public void run() {
            ctx.compileSource(source);
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

    private String submitSource(String source, GlobalContext globalContext, long timeout) throws InterruptedException {
        // This is not side-effecting.
        Tracer coverageTracer = new Tracer.Instrumenting();
        OutputTracer outputTracer = new OutputTracer();
        FinalOutput finalOutput = new FinalOutput();

        RunContext runContext = globalContext.newRun();

        // Create a new compiler context and a runner.
        RunnerThread runnerThread = new RunnerThread(source, runContext);
        Thread newThread = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    runnerThread.run();
                } catch (Throwable e) {
                    finalOutput.exception = new ExcSignature(e);
                    finalOutput.lastPhase = runnerThread.ctx.getPhaseName();
                }
            }
        });
        newThread.setUncaughtExceptionHandler((t, e) -> {
            finalOutput.exception = new ExcSignature(e);
            finalOutput.lastPhase = runnerThread.ctx.getPhaseName();
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
        finalOutput.messages = runnerThread.ctx.getMessages();
        finalOutput.time = endTime - startTime;

        for (BranchHit bh : hits) {
            finalOutput.hits.put(bh.branchHash, bh.hitCount);
        }

        return GSON.toJson(finalOutput);
    }

    static byte[] readBytes(InputStream is) throws IOException {
        byte[] arr = new byte[8192];

        byte[] result;
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            Throwable exc = null;

            try {
                int numBytes;
                while((numBytes = is.read(arr, 0, arr.length)) > 0) {
                    buf.write(arr, 0, numBytes);
                }

                result = buf.toByteArray();
            } catch (Throwable var21) {
                exc = var21;
                throw var21;
            } finally {
                if (exc != null) {
                    try {
                        buf.close();
                    } catch (Throwable e) {
                        exc.addSuppressed(e);
                    }
                } else {
                    buf.close();
                }

            }
        } finally {
            is.close();
        }

        return result;
    }

    public void run(String[] args) throws Exception {
        OptionParser parser = new OptionParser();
        OptionSpec<Double> timeoutOpt = parser.accepts("timeout").withRequiredArg().ofType(Double.class).defaultsTo(30.0);
        OptionSpec<String> argsOpt = parser.accepts("args").withRequiredArg().defaultsTo(
                "-usejavacp -Ycheck:all -Ycheck-mods " +
                "-Ydebug -Ydebug-missing-refs " +
                "-Ydump-sbt-inc -Yforce-sbt-phases " +
                "-Xverify-signatures");
        OptionSpec<Integer> httpOpt = parser.accepts("http").withRequiredArg().ofType(Integer.class).defaultsTo(-1);
        OptionSet optionSet = parser.parse(args);

        long timeout = (long) (optionSet.valueOf(timeoutOpt) * 1000);
        if (timeout < 0) {
            System.err.println("Invalid timeout value.");
            return;
        }

        String compilerArgs = optionSet.valueOf(argsOpt);

        GlobalContext globalContext = newContext(compilerArgs);

        int port = (int) (optionSet.valueOf(httpOpt));
        if (port > 0) {
            HttpServer server = HttpServer.create();
            server.bind(new InetSocketAddress("0.0.0.0", port), 0);
            server.setExecutor(Executors.newCachedThreadPool());

            server.createContext("/submit", new HttpHandler() {
                @Override
                public void handle(HttpExchange httpExchange) throws IOException {
                    String body = new String(readBytes(httpExchange.getRequestBody()), "UTF-8");

                    String result;
                    try {
                        result = submitSource(body, globalContext, timeout);
                    } catch (InterruptedException e) {
                        result = "Interrupted";
                    }

                    final byte[] out = result.getBytes("UTF-8");

                    httpExchange.sendResponseHeaders(200, out.length);

                    OutputStream os = httpExchange.getResponseBody();
                    os.write(out);
                    os.close();
                }
            });

            server.start();
        } else {
            StringBuilder builder = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
            String line;
            while (true) {
                line = reader.readLine();
                if (line == null) {
                    String result = submitSource(builder.toString(), globalContext, timeout);
                    System.out.println(result);
                    break;
                }

                while (line.contains("\1")) {
                    int i = line.indexOf('\1');
                    builder.append(line, 0, i);
                    String source = builder.toString();

                    // Submit the source.
                    String result = submitSource(source, globalContext, timeout);
                    System.out.println(result);

                    // Reset the buffer.
                    builder.setLength(0);
                    line = line.substring(i + 1);
                }
                builder.append(line).append('\n');
            }
        }
    }
}