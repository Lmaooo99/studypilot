# Development Log

## My project decisions

- I chose an AI study assistant because I wanted help studying my own course materials.
- I reduced the scope to 12 features to keep the project manageable.
- I reviewed the design drafts and asked for consistency between the features, use cases and UML diagrams.
- I clarified that weak-topic statistics should use graded quiz results, while flashcard reviews are recorded separately.
- I selected the compatible API and model and provided the connection settings.

## My review, integration and validation

- I read through the implementation and worked through the responsibilities of the main classes and methods.
- I reviewed the code against the Stage 1 design, including the feature flow and the required design patterns.
- I integrated the generated parts and checked how the GUI, CLI, facade, services and JSON storage connect.
- I validated the current implementation and reviewed the results as part of accepting the code.

## Completed work

- Added TXT, Markdown and PDF imports with line or page references.
- Added summaries, concept extraction and questions with checked source citations.
- Added flashcards, review records and basic progress/history using JSON files.

## Checks

- All 52 JUnit tests passed with Java 17 using `./mvnw -q clean verify`.
- Real API checks covered summaries, concepts, cited answers and three generated flashcards.
- Separate CLI processes confirmed card mastery, two reviews and two history events after restarting.
- Course isolation and backups of damaged progress/history files were checked.
- Earlier GUI checks covered summaries and input errors.
- I have completed my review and validation of the current implementation. The final GUI walkthrough and demonstration recordings remain to be done.

## Next steps

Finish quizzes, grading, study plans and the multi-step agent, then update the UML,
report and demonstration videos.

## AI support

Codex was used to generate and revise code, prepare tests and troubleshoot errors.
