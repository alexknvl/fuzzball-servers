package com.alexknvl.fuzzball.dotc;

import dotty.tools.dotc.Compiler;
import dotty.tools.dotc.Driver;
import dotty.tools.dotc.Run;
import dotty.tools.dotc.config.CompilerCommand;
import dotty.tools.dotc.config.Settings;
import dotty.tools.dotc.core.Contexts;
import dotty.tools.dotc.interfaces.SimpleReporter;
import dotty.tools.dotc.reporting.Reporter;
import dotty.tools.dotc.reporting.diagnostic.ErrorMessageID;
import dotty.tools.dotc.reporting.diagnostic.Message;
import dotty.tools.dotc.reporting.diagnostic.MessageContainer;
import jwp.fuzz.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.WrongMethodTypeException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import scala.Console$;
import scala.Function0;
import scala.collection.immutable.Seq;
import scala.collection.immutable.Seq$;
import scala.collection.immutable.List;
import scala.collection.immutable.List$;
import tracehash.package$;
//import scala.collection.mutable.Builder;
//import scala.collection.mutable.WrappedArray$;
//import scala.reflect.internal.util.BatchSourceFile;
//import scala.reflect.internal.util.Position;
//import scala.reflect.internal.util.SourceFile;
//import scala.reflect.io.AbstractFile;
//import scala.reflect.io.VirtualFile;
//import scala.tools.cmd.CommandLineParser$;
//import scala.tools.nsc.Global;
//import scala.tools.nsc.Global$;
//import scala.tools.nsc.Settings;
//import scala.tools.nsc.reporters.AbstractReporter;
//import scala.tools.nsc.reporters.NoReporter$;
//import scala.tools.reflect.FrontEnd;

public class Server {
//    public void runFor() {
//        AtomicBoolean stopper = new AtomicBoolean();
//        new Timer().schedule(new TimerTask() {
//            @Override
//            public void run() { stopper.set(true); }
//        }, unit.toMillis(time));
//        fuzz(stopper);
//    }

    private Tracer tracer = new Tracer.Instrumenting();

    private void submitSource(String source) {
        tracer.startTrace(Thread.currentThread());
        CompilationResult result = compileSource(source);
        BranchHit[] hits = tracer.stopTrace(Thread.currentThread());

        StringBuilder builder = new StringBuilder();
        builder.append('{')
               .append("\"hits\":").append(hits.length).append(',')
               .append("\"errors\":").append(result.errorCount).append(',')
               .append("\"warnings\":").append(result.warningCount).append(',');
        if (result.exception != null) {
            builder.append("\"bug\":\"").append(package$.MODULE$.stackTraceHash(result.exception)).append("\"}");
        } else {
            builder.append("\"bug\":null}");
        }

        System.out.println(builder.toString());
    }

    public void run() throws IOException {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        String line;
        while (true) {
            line = reader.readLine();
            if (line == null) {
                submitSource(builder.toString());
                break;
            }

            while (line.contains("\1")) {
                int i = line.indexOf('\1');
                builder.append(line, 0, i);
                String source = builder.toString();

                // Submit the source.
                submitSource(source);

                // Reset the buffer.
                builder.setLength(0);
                line = line.substring(i + 1);
            }
            builder.append(line).append('\n');
        }

//        invoker(new Invoker.WithExecutorService(
//                new ThreadPoolExecutor(5, 10, 30, TimeUnit.SECONDS,
//                                       new ArrayBlockingQueue<>(500), new ThreadPoolExecutor.CallerRunsPolicy())
    }

    public static void main(String[] args) throws IOException {
        new Server().run();
    }

//  static Settings settings = new Settings();
//  static {
//    settings.processArguments(CommandLineParser$.MODULE$.tokenize("-usejavacp -cp . -language:higherKinds"), true);
//  }
//  static Global global = Global$.MODULE$.apply(settings);

    final static class Reporter1 extends Reporter {
        @Override public void doReport(MessageContainer m, Contexts.Context ctx) {
//            Message message = m.contained();
//            if (message.errorId() == ErrorMessageID.NoExplanationID && message.msg().startsWith("exception"))
//                System.err.println(message.msg());
        }
    }

    private final static class CompilationResult {
        public int errorCount;
        public int warningCount;
        public Throwable exception;
    }

    @SuppressWarnings("unchecked") public static CompilationResult compileSource(String s) {
        Driver driver = new Driver();
        Contexts.FreshContext ctx = driver.initCtx().fresh();
        Reporter1 reporter = new Reporter1();
        ctx.setReporter(reporter);
        Settings.ArgsSummary summary = CompilerCommand.distill(new String[] {
                "-usejavacp", "-cp", ".",
                "-Ycheck:all",
                "-Yno-deep-subtypes", "-Yno-double-bindings",
                "-Yforce-sbt-phases", "-color:never",
                "-Xverify-signatures",
                "-Ystop-after:frontend"}, ctx);
        ctx.setSettings(summary.sstate());

        CompilationResult result = new CompilationResult();

        Compiler compiler = driver.newCompiler(ctx);
        Run run = compiler.newRun(ctx);
        try {
            run.compile(s);
        } catch (Throwable e) {
            result.exception = e;
        }

        result.errorCount = reporter.errorCount();
        result.warningCount = reporter.warningCount();
        return result;

//    global.reporter().reset();
//
//    Global.Run run = global.new Run();
//    global.reporter_$eq(NoReporter$.MODULE$);
//
//    BatchSourceFile sourceFile = new BatchSourceFile(new VirtualFile("<memory>"), s.toCharArray());
//
//    Builder<SourceFile, ?> builder1 = List$.MODULE$.<SourceFile>newBuilder();
//    builder1.addOne(sourceFile);
//
//    run.compileSources((List<SourceFile>)builder1.result());
//
//    return run.compiledFiles();
    }
}
