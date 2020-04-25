# pankti ![Build](https://github.com/castor-software/pankti//workflows/build-on-push/badge.svg)

Pankti is a work in progress.

It leverages [Spoon](http://spoon.gforge.inria.fr/index.html) to process Java applications and instrument their pure methods. The states of these instrumented methods are monitored at runtime. Test oracles are dynamically formulated through these observations.

This means that, in the distant future, pankti will convert production traces to test assertions that amplify applications' test suites.

To run pankti,

1. Clone this repository
2. `mvn clean install`
3. `java -jar target/<pankti-version-jar-with-dependencies.jar> <path-to-a-maven-project>`

