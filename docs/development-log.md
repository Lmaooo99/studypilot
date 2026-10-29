# Development Log

## My project decisions

- I chose an AI study assistant because I wanted help studying my own course materials.
- I reduced the scope to 12 features to keep the project manageable.
- I reviewed the design drafts and asked for consistency between the features, use cases and UML diagrams.
- I clarified that weak-topic statistics should use graded quiz results, while flashcard reviews are recorded separately.
- I selected the compatible API and model and provided the connection settings.

## My review, integration and validation (Period 1)

- I read through the implementation and worked through the responsibilities of the main classes and methods.
- I reviewed the code against the Stage 1 design, including the feature flow and the required design patterns.
- I integrated the generated parts and checked how the GUI, CLI, facade, services and JSON storage connect.
- I validated F01-F05 and the review/history implementation and reviewed the results as part of accepting that code.

## Completed work

- Added TXT, Markdown and PDF imports with line or page references.
- Added summaries, concept extraction and questions with checked source citations.
- Added flashcards, review records and basic progress/history using JSON files.
- Added mixed quiz generation, three question creators and saved quizzes.
- Added saved attempts, objective grading and graded-only quiz progress.
- Added short-answer rubric grading, pending retries and self-assessment against the saved answer.
- Added graded-history difficulty suggestions with reasons and a manual override.
- Added weak-topic rankings, insufficient-data labels and an AI explanation with a fallback.
- Added dated study plans, time-budget checks and saved task completion.
- Connected the assistant planner, six tools, saved conversations and execution traces.

## Checks

- All 202 JUnit tests passed with Java 17 using `./mvnw -q clean verify`.
- Real API checks covered summaries, concepts, cited answers and three generated flashcards.
- Separate CLI processes confirmed card mastery, two reviews and two history events after restarting.
- Course isolation and backups of damaged progress/history files were checked.
- Earlier GUI checks covered summaries and input errors.
- The real quiz check generated multiple-choice, true/false and short-answer questions in one response. Separate CLI processes reloaded the same quiz and rejected access from another course.
- Tests covered invalid drafts, bounded replacement, fewer-question results, saved IDs and immutable quiz content.
- Attempt checks covered fresh retakes, blank answers, pending results, score denominators, course isolation and repeated submissions after restarting.
- Two submitted CLI attempts produced four graded answers, three correct answers and two history events. The checks used no API credentials or new model calls.
- Retrying a failed history save restored one event without counting the attempt again.
- A real short-answer grading check resolved one pending answer with one model call. Another attempt used offline self-assessment. After restart, the two attempts had six graded answers, five correct answers and four history events.
- Repeated grading and self-assessment preserved the original answers and submission times. Invalid scores stayed pending; resolved answers entered progress only once.
- Difficulty tests covered the five-answer minimum, exact 50%/80% boundaries, pending exclusion, topic/course isolation and generation overrides.
- Packaged CLI checks restored the suggestions in new processes and confirmed that reading them changed no saved files or made API calls.
- Weak-topic tests covered sorting, tied names, minimum samples, pending/review exclusion, course scope and explanation failure.
- A real API check explained three ranked topics. Missing-key and high-minimum checks retained the report without an AI explanation. Progress, history, quizzes and attempts were unchanged.
- Plan tests covered dates, daily budgets, bounded correction, diagnostic quizzes without history, storage and completion failures.
- A real API reply produced six tasks over three days, at 60 minutes per day. Separate CLI processes restored the plan and recorded one completion once, including after a repeated action. Earlier study records were preserved.
- Agent tests covered the six service-backed tools, plan validation, partial results, clarification, saved messages, course/session isolation and CLI JSON.
- A real run kept progress when summary generation timed out. A later CLI invocation completed progress and summary; a follow-up restored the same material from saved context. A no-history request asked for clarification instead of claiming a weakest topic.
- My review of the Period 1 implementation is complete. Review of the Period 2 code, its GUI walkthrough and demonstration recordings remain to be done.

## Next steps

Finish agent step references and retry/replan recovery, then complete the remaining
verification, UML, report and demonstration videos.

## AI support

Codex was used to generate and revise code, prepare tests and troubleshoot errors.
