![Build](https://github.com/castor-software/pankti//workflows/build-on-push/badge.svg)

# About

This repository contains code for the following research tools:
- **pankti**: Tests from production data ([documentation](#pankti))
- **rick**: Tests + mocks from production data ([documentation](#rick))

---

# pankti

Pankti transforms production workloads into test cases. The test generation pipeline consists of four phases:
1. Extract
2. Instrument
3. Execute
4. Generate

If you use this code for academic research, please cite: "[Production Monitoring to Improve Test Suites](http://arxiv.org/pdf/2012.01198)", In IEEE Transactions on Reliability, 2021. 

```bibtex
@article{arXiv-2012.01198,
 title = {Production Monitoring to Improve Test Suites},
 journal = {IEEE Transactions on Reliability},
 year = {2021},
 doi = {10.1109/TR.2021.3101318},
 author = {Deepika Tiwari and Long Zhang and Martin Monperrus and Benoit Baudry},
}
```

___

### Extract (pankti-extract)
pankti-extract leverages [Spoon](http://spoon.gforge.inria.fr/index.html) to statically analyze Java applications in order to find relevant methods for test generation.
The output is a list of methods that meet the following criteria: 
- they are public
- they are not empty
- they are not abstract, and do not belong to an interface or annotation type
- they are not deprecated and do not belong to a deprecated class
- they are not static

To run **pankti-extract**,

1. Clone this repository
2. `cd /path/to/pankti/pankti-extract/`
3. `mvn clean install`
4. `java -jar target/pankti-extract-<version>-jar-with-dependencies.jar /path/to/maven/project`
  - Available flags: `-h` (`--help`) for usage, `-v` (`--void`) to include methods that return void
5. The output is a CSV file at `/path/to/pankti/pankti-extract/` called _extracted-methods-\<project-name\>.csv_.
6. Generate Descartes report(s) to find pseudo-tested methods in the project (we use the `method.json` files)
  - [pitest-descartes on GitHub](https://github.com/STAMP-project/pitest-descartes)
  - [pitest-descartes for multi-module projects](https://github.com/STAMP-project/pitmp-maven-plugin)
7. `python find-pseudo-tested.py /path/to/method/list/from/step5.csv /space/separated/paths/to/descartes/method.json` outputs a CSV with the list of methods that are candidates for instrumentation.
___

### Instrument (pankti-instrument)
pankti-instrument is a Glowroot ([download](https://glowroot.org/)) plugin that serializes objects for instrumented methods that are invoked. pankti-instrument uses the [Plugin API](https://glowroot.org/instrumentation.html) of Glowroot to instrument the methods extracted from pankti-extract.  

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
Additionally, a list of invoked methods is generated at `/tmp/pankti-object-data/invoked-methods.csv`. 
___

### Generate (pankti-generate)
pankti-generate creates test classes for an application from the collected object profiles.\
It takes as input the path to the Java + Maven project, a CSV file with a list of invoked methods, and the path to the directory containing objects serialized as XML.

To run **pankti-generate**,
1. `cd /path/to/pankti/pankti-generate/`
2. `mvn clean install`
3. `java -jar target/pankti-generate-<version>-jar-with-dependencies.jar /path/to/project /path/to/invoked/methods.csv /path/to/directory/with/objects/`

The output is in a directory at `/path/to/pankti/pankti-generate/output/generated/<project-name>/`. Generated test classes are placed in appropriate package directories. The naming convention followed is _Test\<ClassName\>PanktiGen.java_. Resource files for long XML strings are created at `/path/to/pankti/pankti-generate/output/generated/object-data`.
___

<p align="center">
  <img src="https://github.com/castor-software/pankti/blob/master/pankti-workflow.jpg">
</p>

#### :telescope: Results and data: [pankti-experiments](https://github.com/castor-software/pankti-experiments)
___

# rick

Rick transforms production workloads into tests that use mocks.
In addition to some functionalities and implementation shared with **pankti**,
it supports the instrumentation of methods that can be mocked within a test. It also handles the generation of these mocks with data collected from production.

---

## Building
1. Clone this repository
2. `cd pankti/`
3. `mvn clean install`
---
## Running

To **extract** methods under test (MUTs) and mockable methods:
1. `cd pankti/pankti-extract/`
2. `java -jar target/pankti-extract-<version>-jar-with-dependencies.jar /path/to/maven/project`
3. The output is `./extracted-methods-<project>.csv`

---

To **instrument** MUTs and mockable methods:
1. `cd pankti/pankti-instrument/`
2. Instrument all MUTs found across all classes:
    - `python3 instrument-mock.py ../pankti-extract/extracted-methods-<project>.csv`
3. Alternatively, instrument MUTs found in a specific class under test (CUT):
    - `python3 instrument-mock.py ../pankti-extract/extracted-methods-<project>.csv fully.qualified.name.of.CUT`
4. This generates aspect classes for MUTs and corresponding mockable methods in `se.kth.castor.pankti.instrument.plugins`
5. It also updates `./src/main/resources/META-INF/glowroot.plugin.json`
6. `mvn clean install`
7. Drop `<pankti-instrument-<version>-jar-with-dependencies.jar` to `/path/to/glowroot/plugins/`

---

To **execute** the target project:
1. `java -javaagent:/path/to/glowroot/glowroot.jar <project-jar-and-options>`
    - The collected objects are stored at `/tmp/pankti-object-data/`
    - A CSV with the list of invoked MUTs is at `/tmp/pankti-object-data/invoked-methods.csv`

---

To **generate** tests:
1. `cd pankti/rick/`
2. `java -jar target/rick-<version>-jar-with-depdendencies.jar /path/to/maven/project/ /tmp/pankti-object-data/invoked-methods.csv /tmp/pankti-object-data/`
    - The generated tests (`Test*PanktiGen.java`) are at `./output/generated/<project>`

---
