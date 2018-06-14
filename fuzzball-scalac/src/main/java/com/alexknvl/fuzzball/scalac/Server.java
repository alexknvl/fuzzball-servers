package com.alexknvl.fuzzball.scalac;

import com.alexknvl.fuzzball.common.Runner;
import scala.collection.immutable.List;
import scala.collection.immutable.List$;
import scala.collection.mutable.Builder;
import scala.reflect.internal.util.BatchSourceFile;
import scala.reflect.internal.util.Position;
import scala.reflect.internal.util.SourceFile;
import scala.reflect.io.VirtualFile;
import scala.tools.cmd.CommandLineParser$;
import scala.tools.nsc.Global;
import scala.tools.nsc.Global$;
import scala.tools.nsc.Settings;

import java.util.ArrayList;

public class Server extends Runner {
    public static void main(String[] args) throws Exception {
        new Server().run(args);
    }

    private static final class ThisContext implements CompilerContext {
        private final Global global;
        private final Global.Run run;

        private ArrayList<Message> messages = new ArrayList<>();

        private final class CustomReporter extends scala.tools.nsc.reporters.ConsoleReporter {
            public CustomReporter(Settings settings) {
                super(settings);
            }

            @Override public void info0(Position pos, String msg, Severity severity, boolean force) {
                String level = "unknown";
                if (severity == INFO()) level = "info";
                if (severity == WARNING()) level = "warn";
                if (severity == ERROR()) level = "error";

                String position = pos.line() + ":" + pos.column();
                String phase = getPhaseName();

                if (!level.equals("info"))
                    messages.add(new Message(level, msg, position, phase));
            }
        }

        ThisContext(String compilerArgs) {
            Settings settings = new Settings();
            settings.processArguments(CommandLineParser$.MODULE$.tokenize(compilerArgs), true);
            settings.maxerrs().value_$eq(Integer.MAX_VALUE);
            settings.maxwarns().value_$eq(Integer.MAX_VALUE);
            global = Global$.MODULE$.apply(settings);

            global.reporter().reset();
            run = global.new Run();
            global.reporter_$eq(new CustomReporter(settings));
        }

        @Override public java.util.List<Message> getMessages() {
            return messages;
        }

        @Override public String getPhaseName() {
            return global.globalPhase().name();
        }
    }

    @Override protected CompilerContext newContext(String compilerArgs) {
        return new ThisContext(compilerArgs);
    }

    @Override protected void compileSource(CompilerContext context, String source) {
        BatchSourceFile sourceFile = new BatchSourceFile(new VirtualFile("<memory>"), source.toCharArray());
        Builder<SourceFile, ?> builder1 = List$.MODULE$.<SourceFile>newBuilder();
        builder1.addOne(sourceFile);
        @SuppressWarnings("unchecked") List<SourceFile> sources = (List<SourceFile>) builder1.result();

        ((ThisContext) context).run.compileSources(sources);
    }
}
