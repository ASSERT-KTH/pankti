# pankti ![Build](https://github.com/castor-software/pankti//workflows/build-on-push/badge.svg)

Pankti is a work in progress.

It leverages [Spoon](http://spoon.gforge.inria.fr/index.html) to process Java applications. A set of methods in the application are instrumented. The states of these instrumented methods are monitored at runtime. Test oracles are dynamically formulated through these observations.

Currently, pankti finds a set of "pure" methods in a Java application. It follows a conservative definition of purity.\
A pure method
- is not empty
- is not abstract and does not belong to an interface or annotation type
- does not throw exceptions
- is not synchronized and does not have synchronized blocks
- is not deprecated and does not belong to a deprecated class
- does not invoke other methods
- does not invoke constructors (create new objects)
- does not modify fields
- does not modify arrays passed as arguments
- does not modify non-local variables
- may accept arguments, define new local variables, read-access fields, perform computations
- returns a value

When fully implemented, pankti will convert production traces to test assertions that amplify applications' test suites.

To run pankti,

1. Clone this repository
2. `mvn clean install`
3. `java -jar target/<pankti-version-jar-with-dependencies.jar> <path-to-a-maven-project>`
___

Preliminary experiments
- [Finding pure methods in some Java applications](https://github.com/Deee92/journal/blob/master/notes/pankti-analysis.md)
- [105 pure methods in jitsi-videobridge](https://github.com/Deee92/journal/blob/master/notes/jitsi-analysis.md)

