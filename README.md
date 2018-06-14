# Fuzzball Servers

Compilation servers that can be used for testing Dotty or Scala. Part of the larger vision for [fuzzball](https://github.com/alexknvl/fuzzball), the scala fuzzer.

## Compiling
You *will* need to modify `build.gradle` and set the right dotty version (i.e. a locally published SNAPSHOT). You may need to compile & install [tracehash](https://github.com/alexknvl/tracehash) and add it to dependencies in `:fuzzball-dotc`. Please ask @alexknvl on [Dotty Gitter](https://gitter.im/lampepfl/dotty) if you need any help running it.

## Running
```
./fuzzball-dotc/build/install/fuzzball-dotc/bin/fuzzball-dotc
```

Reads `\1` separated source files from the standard output and runs them through the dotty compiler,
collecting coverage and error statistics:

```
{
    "exception": {
        "className": "java.lang.AssertionError",
        "hash": "AE-88e48d4972cb198bb4390f4c028ba1065e365aa3",
        "message": "assertion failed",
        "principal": [
            {
                "declaringClass": "dotty.DottyPredef$",
                "fileName": "DottyPredef.scala",
                "lineNumber": 35,
                "methodName": "assertFail"
            },
            {
                "declaringClass": "dotty.tools.dotc.util.Positions$Position$",
                "fileName": "Positions.scala",
                "lineNumber": 40,
                "methodName": "start$extension"
            },
            {
                "declaringClass": "dotty.tools.dotc.util.SourcePosition",
                "fileName": "SourcePosition.scala",
                "lineNumber": 45,
                "methodName": "start"
            },
            {
                "declaringClass": "dotty.tools.dotc.util.SourcePosition",
                "fileName": "SourcePosition.scala",
                "lineNumber": 46,
                "methodName": "startLine"
            },
            {
                "declaringClass": "com.alexknvl.fuzzball.dotc.Server$ThisContext$CustomReporter",
                "fileName": "Server.java",
                "lineNumber": 41,
                "methodName": "doReport"
            }
        ]
    },
    "hits": {
        "-1007018313": 486,
        "-1007630627": 2,
        "-1007630726": 6,
        ... thousands of entries
    },
    "lastPhase": "frontend",
    "messages": [
        {
            "level": "error",
            "message": "']' expected, but '<:' found",
            "phase": "frontend",
            "position": "4:26-4:28"
        },
        {
            "level": "error",
            "message": "i1 is already defined as type i1",
            "phase": "frontend",
            "position": "2:13-2:26"
        }
    ],
    "stdErr": "",
    "stdOut": "",
    "time": 1274
}
```

 * If `exception` is present in the output, it contains information about an exception that resulted in compiler's termination. 
   + `hash` is [tracehash](https://github.com/alexknvl/tracehash)-style signature.
   + `principal` is the most significant part of the stack trace (see [tracehash's README](https://github.com/alexknvl/tracehash) for an explanation).
   + `lastPhase` is the phase when the exception happened.
 * `hits` are covered branches with hit counts.
 * `messages` are collected warnings and errors.
 * `stdErr` is standard error output.
 * `stdOut` is standard output.
 * `time` is the time it took to run the compiler (not very accurate).
 
## Potential Applications

 * Fuzzing (e.g. using [fuzzball](https://github.com/alexknvl/fuzzball)).
 * [Compiler Validation via Equivalence Modulo Inputs](https://mehrdad.afshari.me/publications/compiler-validation-via-equivalence-modulo-inputs.pdf).
 * Automatic test case minimization a la [C-Reduce](https://embed.cs.utah.edu/creduce/).
   
## Credits
Code is largely derived from [Javan Warty Pig](https://github.com/cretz/javan-warty-pig).
