package com.alexknvl.fuzzball.dotc;

import com.alexknvl.fuzzball.common.Runner;
import dotty.tools.dotc.Compiler;
import dotty.tools.dotc.Driver;
import dotty.tools.dotc.Run;
import dotty.tools.dotc.config.CompilerCommand;
import dotty.tools.dotc.config.Settings;
import dotty.tools.dotc.core.Contexts;
import dotty.tools.dotc.core.Phases;
import dotty.tools.dotc.interfaces.Diagnostic;
import dotty.tools.dotc.profile.ProfileSnap;
import dotty.tools.dotc.profile.Profiler;
import dotty.tools.dotc.profile.Profiler$;
import dotty.tools.dotc.reporting.Reporter;
import dotty.tools.dotc.reporting.diagnostic.MessageContainer;

import java.util.ArrayList;
import java.util.List;

public class Server extends Runner {
    public static void main(String[] args) throws Exception {
        new Server().run(args);
    }

    private static final class ThisContext implements CompilerContext {
        private final Driver driver;
        private final CustomReporter reporter;
        private final Compiler compiler;
        private final Run run;

        private ArrayList<Message> messages = new ArrayList<>();

        final class CustomReporter extends Reporter {
            @Override public void doReport(MessageContainer m, Contexts.Context ctx) {
                String level = "unknown";
                if (m.level() == Diagnostic.INFO) level = "info";
                if (m.level() == Diagnostic.WARNING) level = "warn";
                if (m.level() == Diagnostic.ERROR) level = "error";

                String position = m.pos().startLine() + ":" + m.pos().startColumn() + "-" + m.pos().endLine() + ":" + m.pos().endColumn();
                String message = m.getMessage();
                String phase = ctx.phase().phaseName();

                if (!level.equals("info"))
                    messages.add(new Message(level, message, position, phase));
            }
        }

        ThisContext(String compilerArgs) {
            driver = new Driver();
            Contexts.FreshContext ctx = driver.initCtx().fresh();

            Settings.ArgsSummary summary = CompilerCommand.distill(compilerArgs.split("\\s+"), ctx);
            ctx.setSettings(summary.sstate());

            reporter = new CustomReporter();
            ctx.setReporter(reporter);

            compiler = driver.newCompiler(ctx);
            run = compiler.newRun(ctx);
        }

        @Override public List<Message> getMessages() {
            return messages;
        }

        @Override public String getPhaseName() {
            return run.ctx().phase().phaseName();
        }
    }

    @Override protected CompilerContext newContext(String compilerArgs) {
        return new ThisContext(compilerArgs);
    }

    @SuppressWarnings("unchecked") @Override
    public void compileSource(CompilerContext context, String source) {
        ((ThisContext) context).run.compile(source);
    }
}
