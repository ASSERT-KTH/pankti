# pankti ![Build](https://github.com/castor-software/pankti//workflows/build-on-push/badge.svg)

Pankti transforms production workload into test cases. The test generation pipeline consists of four phases:
1. Extract
2. Instrument
3. Execute
4. Generate
___
### Extract (pankti-extract)
This phase leverages [Spoon](http://spoon.gforge.inria.fr/index.html) to statically analyze Java applications built with Maven.
The output is a list of methods that meet the following criteria: 
- they are not empty
- they are not abstract, and do not belong to an interface or annotation type
- they are not deprecated and do not belong to a deprecated class
- they are not static
- they return a value

To run **pankti-extract**,

1. Clone this repository
2. `cd /path/to/pankti/pankti-extract/`
3. `mvn clean install`
4. `java -jar target/pankti-extract-<version>-jar-with-dependencies.jar /path/to/maven/project`
5. The output is a CSV file at `/path/to/pankti/pankti-extract/` called _extracted-methods-\<project-name\>.csv_.
6. Generate Descartes report(s) to find pseudo-tested methods in the project (we use the `method.json` files)
  - [pitest-descartes on GitHub](https://github.com/STAMP-project/pitest-descartes)
  - [pitest-descartes for multi-module projects](https://github.com/STAMP-project/pitmp-maven-plugin)
7. `python find-pseudo-tested.py /path/to/method/list/from/step5.csv /space/separated/paths/to/descartes/method.json` outputs a CSV with the list of methods that are candidates for instrumentation.
___
### Instrument (pankti-instrument)
This phase develops a [Glowroot](https://glowroot.org/) plugin that serializes objects for instrumented methods that are invoked.

To run **pankti-instrument**,
1. `cd /path/to/pankti/pankti-instrument/`
2. `python instrument.py <path/to/instrumentation/candidates/from/previous/phase>.csv`
3. New aspect classes for these methods are generated in `se.kth.castor.pankti.instrument.plugins`. These aspect classes are also included in `./src/main/resources/META-INF/glowroot.plugin.json`
4. `mvn clean install`
5. Drop `<pankti-instrument-<version>-jar-with-dependencies.jar` to `/path/to/glowroot/plugins/` 
___

### Execute
Execute the application with a workload, using Glowroot as a javaagent.\
`java -javaagent:/path/to/glowroot/glowroot.jar -jar <project-jar>.jar <cli-args>`\
The serialized objects for invoked methods are saved at `/tmp/pankti-object-data/`.
Additionally, a CSV file with a list of invoked methods is generated at `/tmp/pankti-object-data/`. 
___

### Generate (pankti-generate)
This phase uses the code generation features of Spoon to create test classes for an application.\
It takes as input the path to the Java + Maven project, a CSV file with a list of invoked methods, and the path to the directory containing objects serialized as XML.

To run **pankti-generate**,
1. `cd /path/to/pankti/pankti-generate/`
2. `mvn clean install`
3. `java -jar target/pankti-generate-<version>-jar-with-dependencies.jar /path/to/project /path/to/invoked/methods.csv /path/to/directory/with/objects/`

The output is in a directory at `/path/to/pankti/pankti-generate/output/generated/<project-name>/`. Generated test classes are placed in appropriate package directories. The naming convention followed is _Test\<ClassName\>PanktiGen.java_. Resource files for long XML strings are created at `/path/to/pankti/pankti-generate/output/generated/object-data`.
___
