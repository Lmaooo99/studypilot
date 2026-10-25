# Development Log

## My project decisions

- I chose an AI study assistant because I wanted help studying my own course materials.
- I reduced the scope to 12 features to keep the project manageable.
- I reviewed the design drafts and asked for consistency between the features, use cases and UML diagrams.
- I clarified that weak-topic statistics should use graded quiz results, while flashcard reviews are recorded separately.
- I selected the compatible API and model and provided the connection settings.

## My review, integration and validation

- I read through the implementation and worked through the responsibilities of the main classes and methods.
- I reviewed the code against the Stage 1 design, including the feature flow and required patterns.
- I integrated the generated parts and checked the connections between components.
- I validated the implementation and reviewed the results.

## C01 Setup

Added the Maven build, command-line startup, Swing window and local configuration.
Feature screens are marked as planned until their implementation is added.

## Checks

All 8 JUnit tests passed with Java 17 using `./mvnw -q clean verify`.
The packaged CLI help and status commands also ran successfully.

## Next steps

Add text and Markdown imports, then PDF imports and the study features.

## AI support

Codex was used to generate and revise code, prepare tests and troubleshoot errors.
