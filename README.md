# StudyPilot

StudyPilot is an AI study and learning assistant for EECS3311. The design baseline
is [Stage1-Report.pdf](Stage1-Report.pdf).

## Current functionality

Create courses and import TXT or Markdown materials through the GUI or CLI.
Imports keep the file name and original line ranges. PDF imports and the AI study
features will be added in later batches.

## Build and run

Use JDK 17 or newer. The first Maven Wrapper build needs Internet access.

```sh
./mvnw clean verify
java -jar target/studypilot.jar help
java -jar target/studypilot.jar status
java -jar target/studypilot.jar gui
```

On Windows, use mvnw.cmd. Eclipse can import the repository as a Maven project.

## Course materials

```sh
java -jar target/studypilot.jar course add "Software Design"
java -jar target/studypilot.jar course list
java -jar target/studypilot.jar material import samples/design-notes.md --course COURSE_ID
java -jar target/studypilot.jar material list --course COURSE_ID
```

Replace COURSE_ID with the ID printed when creating a course. Imports accept
.txt and .md files up to 20 MiB. Empty, unreadable, unsupported and duplicate files
are rejected. Long text is split into overlapping chunks while preserving complete
Unicode characters and line ranges.

## Configuration and storage

Runtime files are saved under data/courses by default. A new process reloads the
saved course, material and chunk IDs. DATA_DIR can be set in a local .env file.
Copy .env.example to .env before configuring later AI features. Keep real settings
local. Course creation and text imports do not require an API key.

## Development records

See [the development log](docs/development-log.md) for decisions and checks, and
[the commit summary](docs/period-1-commits.md) for publication progress.
