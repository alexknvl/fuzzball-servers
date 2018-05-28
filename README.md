# Fuzzball Servers

Compilation servers that can be used for fuzzing dotty or Scala
(not yet). Part of the larger vision for [fuzzball](https://github.com/alexknvl/fuzzball),
the scala fuzzer.

## Compiling
You *will* need to modify `build.gradle` and set the right dotty version (i.e. a locally published SNAPSHOT). You may need to compile & install [tracehash](https://github.com/alexknvl/tracehash) and add it to dependencies in `:fuzzball-dotc`. Please ask @alexknvl on [Dotty Gitter](https://gitter.im/lampepfl/dotty) if you need any help running it.

## Running
```
./fuzzball-dotc/build/install/fuzzball-dotc/bin/fuzzball-dotc
```

Reads `\1` separated source files from the standard output and runs them through the dotty compiler,
collecting coverage and error statistics:

```
{"hits":3436,"errors":21,"warnings":0,"bug":"SOE-e4f10db9ea4f061b151be30b8b50780c2b4642f4"}
{"hits":3039,"errors":12,"warnings":0,"bug":"AE-c1180b713646bf84b82e5ae3df2d3252366db23c"}
{"hits":2032,"errors":5,"warnings":0,"bug":null}
{"hits":2288,"errors":11,"warnings":0,"bug":null}
```

 * `hits` is the number of branches covered.
 * `errors` is the number of errors.
 * `warnings` is the number of warnings.
 * `bug` is `null` if there were no exceptions during compilation and
   [tracehash](https://github.com/alexknvl/tracehash)-style signature
   otherwise.
   
## Credits
Code is largely derived from [Javan Warty Pig](https://github.com/cretz/javan-warty-pig).
