# pankti ![Build](https://github.com/castor-software/pankti//workflows/build-on-push/badge.svg)

Pankti transforms production workload into test cases. The test generation pipeline consists of four phases:
1. Extract
2. Instrument
3. Execute
4. Generate
---
### Extract (pankti-extract)
This phase leverages [Spoon](http://spoon.gforge.inria.fr/index.html) to statically analyze Java applications built with Maven.
The output is a list of methods that meet the following criteria: 
- they are not empty
- they are not abstract, and do not belong to an interface or annotation type
- they do not throw exceptions
- they are not synchronized, and do not have synchronized statements or blocks
- they are not deprecated and do not belong to a deprecated class
- they do not invoke other methods
- they do not invoke constructors (create new objects)
- they do not modify fields
- they do not modify arrays passed as arguments
- they do not modify non-local variables
- they may accept arguments, define new local variables, read-access fields, perform computations
- they return a value

To run **pankti-extract**,

1. Clone this repository
2. `cd /path/to/pankti/pankti-extract/`
3. `mvn clean install`
4. `java -jar target/pankti-extract-<version>-jar-with-dependencies.jar /path/to/maven/project`
5. The output is a CSV file at `/path/to/pankti/pankti-extract/` called _extracted-methods-\<project-name\>.csv_.
6. `python filter.py /path/to/CSV/from/step/5.csv` gives the list of methods that are candidates for instrumentation
___
### Generate (pankti-generate)
This phase uses the code generation features of Spoon to create test classes for an application.\
It takes as input the path to the Java + Maven project, a CSV file with a list invoked methods, and the path to the directory containing objects serialized as XML.

To run **pankti-generate**,
1. `cd /path/to/pankti/pankti-generate/`
2. `mvn clean install`
3. `java -jar target/pankti-generate-<version>-jar-with-dependencies.jar /path/to/project /path/to/invoked/methods.csv /path/to/directory/with/objects/`

The output is in a directory at `/path/to/pankti/pankti-generate/output/generated/<project-name>/`. Generated test classes are placed in appropriate package directories. The naming convention followed is _Test\<ClassName\>PanktiGen.java_. Resource files for long XML strings are created at `/path/to/pankti/pankti-generate/output/generated/object-data`.

