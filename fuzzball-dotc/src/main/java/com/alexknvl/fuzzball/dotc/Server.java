package com.alexknvl.fuzzball.dotc;

import com.alexknvl.fuzzball.common.Runner;
import dotty.tools.dotc.Compiler;
import dotty.tools.dotc.Driver;
import dotty.tools.dotc.Run;
import dotty.tools.dotc.config.CompilerCommand;
import dotty.tools.dotc.config.Settings;
import dotty.tools.dotc.core.Comments;
import dotty.tools.dotc.core.Contexts;
import dotty.tools.dotc.interfaces.Diagnostic;
import dotty.tools.dotc.reporting.Reporter;
import dotty.tools.dotc.reporting.diagnostic.MessageContainer;

import java.util.ArrayList;
import java.util.List;

public class Server extends Runner {
    public static void main(String[] args) throws Exception {
        new Server().run(args);
    }

    final static class CustomReporter extends Reporter {
        public final ArrayList<Message> messages;

        CustomReporter(ArrayList<Message> messages) {
            this.messages = messages;
        }

        @Override public void doReport(MessageContainer m, Contexts.Context ctx) {
            try {
                String level = "unknown";
                if (m.level() == Diagnostic.INFO) level = "info";
                if (m.level() == Diagnostic.WARNING) level = "warn";
                if (m.level() == Diagnostic.ERROR) level = "error";

                String position;
                if (m.pos().exists()) position =
                        m.pos().startLine() + ":" + m.pos().startColumn() + "-" + m.pos().endLine() + ":" + m.pos
                                ().endColumn();
                else position = "<unk>";

                String message = m.getMessage();
                String phase = ctx.phase().phaseName();

                if (!level.equals("info")) messages.add(new Message(level, message, position, phase));
            } catch (Throwable e) {
                System.err.println("Caught an exception while saving a message.");
                e.printStackTrace(System.err);
            }
        }
    }

    private static final class ThisGlobalContext implements GlobalContext {
        private final Driver driver;
        private final CustomReporter reporter;
        private final Compiler compiler;
        private final ArrayList<Message> messages;

        ThisGlobalContext(String compilerArgs) {
            driver = new Driver();
            Contexts.FreshContext ctx = driver.initCtx().fresh();

            Settings.ArgsSummary summary = CompilerCommand.distill(compilerArgs.split("\\s+"), ctx);
            ctx.setSettings(summary.sstate());
            ctx.setProperty(Comments.ContextDoc(), new Comments.ContextDocstrings());

            messages = new ArrayList<>();
            reporter = new CustomReporter(messages);
            ctx.setReporter(reporter);

            compiler = driver.newCompiler(ctx);
        }

        @Override
        public RunContext newRun() {
            return new ThisRunContext(compiler.newRun(ctx), messages);
        }
    }

    private static final class ThisRunContext implements RunContext {
        private final Run run;
        private final ArrayList<Message> messages;

        ThisRunContext(Run run, ArrayList<Message> messages) {
            this.run = run;
            this.messages = messages;
        }

        @Override
        public void compileSource(String source) {
            messages.clear();
            run.compileFromStrings(scala.collection.immutable.List.fill(1, new scala.Function0<String>() {
                @Override
                public String apply() {
                    return source;
                }
            }).toList());
        }

        @Override public List<Message> getMessages() {
            return messages;
        }

        @Override public String getPhaseName() {
            return run.ctx().phase().phaseName();
        }
    }

    @Override protected GlobalContext newContext(String compilerArgs) {
        return new ThisGlobalContext(compilerArgs);
    }
}
