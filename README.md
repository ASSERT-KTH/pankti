# pankti

Pankti is a work in progress.

It leverages [Spoon](http://spoon.gforge.inria.fr/index.html) to process Java applications and instrument their pure methods. The states of these instrumented methods are monitored at runtime. Test oracles are dynamically formulated through these observations.

This means that, in the distant future, pankti will convert production traces to test assertions that amplify applications' test suites.

To run pankti,

1. Clone this repo
2. Clone [this sample repository](https://github.com/Deee92/spoon-dog) (to be updated with a more suitable sample repo soon!)
3. Replace the path in `setUpLauncherAndModel()` in `src/java/test/processors/FirstMethodProcessor.java` with path of cloned sample repository
4. `mvn clean install`
5. `java -jar target/pankti-1.0-SNAPSHOT-jar-with-dependencies.jar <path-to-a-maven-project>`

