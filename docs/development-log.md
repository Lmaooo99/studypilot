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
- Added step-result references, bounded retries/replanning and reuse of completed agent actions.
- Added saved review events, progress/history restoration and duplicate-count checks after interrupted writes.
- Added material/quiz persistence integration tests and stricter checks for saved source locations and question numbers.
- Added grading, difficulty and study-plan integration checks and validation of saved grading fields.
- Added service-backed agent recovery and saved-conversation isolation tests.
- Added CLI input/structured-result checks and rejected agent options used in place of a request.
- Added complete study/agent restart workflows and checked recovery without invented history.
- Updated the UMLet sources/images and prepared the seven-section Word/PDF report and captured CLI replays.

## Checks

- At C21 on November 3, all 307 JUnit tests passed with Java 17 using `./mvnw -q clean verify`; this is a historical total. The later November 9 verification passed 361 tests.
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
- Recovery tests counted tool dispatches, checked the two-retry/two-replan limits, validated dependencies and preserved successful results across replans.
- A live check used one real generated question and five intentionally incorrect submissions in a separate verification course. The assistant read that graded topic and used its returned field to generate a quiz. A new process restored the session; no-history input asked for clarification.
- Recovery checks restored counts and history from saved records, preserved damaged-file backups and kept healthy reads unchanged. The 26 packaged CLI checks used no API credentials or new model calls.
- Persistence checks retained original IDs, source citations and typed quiz data across fresh application instances. Seventeen new tests and 29 offline CLI checks covered these paths and rejected malformed saved fields.
- Fourteen new integration checks and 46 offline CLI checks covered pending resolutions, difficulty thresholds, graded evidence for plans, budget/date constraints and task completion. No provider request was made for these checks.
- Fifteen new agent integration tests covered service retry limits, retained results and separate course/session histories. The 28 offline CLI checks preserved failed turns and damaged conversation files without a provider request.
- Thirteen new CLI tests checked JSON fields, partial/recovered results, clarification, retries, warnings and input errors. The 33 packaged CLI checks verified actual exit codes, preserved requests and JSON/stderr separation without a provider request.
- Three complete restart tests checked saved study content, grading, feedback, task completion and dependent agent practice through real services and repositories with the existing test-only LLM client.
- A separate production CLI workflow used 13 actual API responses, including a real weak-topic-to-quiz reference and a saved-conversation follow-up. Its repeated empty submissions are labelled demonstration inputs, not student study results.
- My review of the Period 1 implementation is complete. Review of the Period 2 and Period 3 code, the final GUI walkthrough and demonstration recordings remain to be done.

## Next steps

Complete the native GUI walkthrough and recording, review the later implementation,
and verify the final published assets from a fresh clone. Upload the videos and add
accessible timestamp links before the student submits the repository URL.

## AI support

Codex was used to generate and revise code, prepare tests and troubleshoot errors.


## November 10 native walkthrough and delivery verification

The student completed focused guided review questions about later core paths,
supplied a successful 361-test build summary and recorded the native GUI/CLI.
All sixteen clips were joined without content cuts. Actual feature timestamps
are in demo/README.md; the older rendered CLI panels are superseded.

An isolated Java 17 ./mvnw -o clean verify independently passed 361 tests in 46
classes, with no failures, errors or skips. The recorded JAR and the 135-file Java
source snapshot match their preparation fingerprints. Source recordings are unchanged.
This build used no credentials or live model calls; the video separately shows
actual model-backed application actions. This is not KUMA evaluation.

Retained Codex/Claude project logs were deduplicated to recover scoped usage.
They do not establish complete project billing. No staging, commit or push was
performed for this delivery preparation, and no course submission was made.


The November 10 native demonstration was uploaded to YouTube as Unlisted: [https://www.youtube.com/watch?v=eNrlBO0PAuY](https://www.youtube.com/watch?v=eNrlBO0PAuY). Signed-out Chrome Incognito playback passed; all twelve feature and two CLI timestamp links sought to their specified positions. The player offers 1080p60 HD. This checks viewing access and navigation, not KUMA behavior. The repository remains a local working copy relative to C21; no release or fresh published-clone check is claimed.


## November 10 scoped tool usage estimate

Per-response Codex usage from the Stage 2 request on October 24 at 20:31 EDT to
November 10 at 22:34 EST gives 523,736,215 tokens in 1,968 requests. This includes
the main conversation, sixteen recorded background compactions and nine verified
video-agent requests. Response IDs were deduplicated; inherited counters, cached
input and reasoning were not added twice. The prior 21:59 snapshot remains a
historical subset and is not added to this figure.

Current gpt-6.1-sol Standard API prices give an equivalent cost of $156.22, with
long-context rates applied per request. The student-supplied Claude Code screenshot
reports about 287 million tokens and $125.60 equivalent cost for November 9-10,
including an estimate for four unlogged compactions. The two estimates total about
$281.82. These are API equivalents, not verified invoices or allocated subscription
payments. App model and transcription fees are separate. The report and usage
checkpoint record the scope and assumptions without private conversations or keys.
No Java source, staging, commit, push or course submission changed in this step.
