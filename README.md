# StudyPilot

StudyPilot is an AI study and learning assistant for EECS3311. The design baseline
is Stage1-Report.pdf.

## Current batch

C01 provides the Maven build, CLI/GUI startup and local configuration.
Course features will be added in the following verified batches.

## Build and run

Use JDK 17 or newer. The first build needs Internet access for Maven dependencies.

```sh
./mvnw clean verify
java -jar target/studypilot.jar help
java -jar target/studypilot.jar status
java -jar target/studypilot.jar gui
```

On Windows, use mvnw.cmd. Eclipse can import this as a Maven project.
Copy .env.example to .env before using later AI features. Keep real API settings
local. Startup does not need a key.

## Development records

See [the development log](docs/development-log.md) for project decisions and checks,
and [the commit summary](docs/period-1-commits.md) for publication progress.
