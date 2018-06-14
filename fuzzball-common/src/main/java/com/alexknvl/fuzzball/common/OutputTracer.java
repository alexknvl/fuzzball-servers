package com.alexknvl.fuzzball.common;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

public final class OutputTracer {
    private PrintStream oldErr = System.err;
    private PrintStream oldOut = System.out;
    private ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    private ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

    public static final class Result {
        public final String outBuf;
        public final String errBuf;

        public Result(String outBuf, String errBuf) {
            this.outBuf = outBuf;
            this.errBuf = errBuf;
        }
    }

    public void startTrace() {
        oldErr = System.err;
        oldOut = System.out;
        outBuf = new ByteArrayOutputStream();
        errBuf = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(outBuf, true, "UTF-8"));
            System.setErr(new PrintStream(errBuf, true, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new Error(e);
        }
    }

    public Result stopTrace() {
        try {
            return new Result(
                    new String(outBuf.toByteArray(), StandardCharsets.UTF_8),
                    new String(errBuf.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            System.setErr(oldErr);
            System.setOut(oldOut);
        }
    }
}