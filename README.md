# StudyPilot

StudyPilot is the EECS3311 course project for an AI study and learning agent.
The Stage 1 design is in [Stage1-Report.pdf](Stage1-Report.pdf).

## Current status

Period 1 implements F01-F05: course/material import, summaries, concepts,
questions with checked citation IDs, and flashcard generation/review. F11 currently
records reviews and quiz history. Period 2 adds F06 quiz generation, F07 difficulty
suggestions, F08 saved attempts with objective and model-based grading, and F09
weak-topic reports. Failed grades can be retried or self-assessed against the saved
answer and rubric. Study plans and the multi-step agent remain on the task board.

## Requirements and build

- JDK 17 or newer
- Internet access for the first Maven Wrapper build

```sh
./mvnw clean verify
java -jar target/studypilot.jar help
java -jar target/studypilot.jar status
java -jar target/studypilot.jar gui
```

On Windows, use `mvnw.cmd clean verify` for the build. Eclipse can import the
repository as an existing Maven project.

## Local configuration

Copy `.env.example` to `.env` and enter your own OpenAI-compatible API settings.
The base URL must include the provider's API version path. The application will
append `/chat/completions`. Use HTTPS; HTTP is allowed only for local test servers.
The default model is `gpt-6.1-sol`. Availability depends on your provider and account.
Courses, imports, reviews and progress work without an API key.

```sh
cp .env.example .env
```

Never commit `.env`, credentials, runtime data, or raw AI conversation logs.
The example configuration contains placeholders only.

## Course material commands

```sh
java -jar target/studypilot.jar course add "Software Design"
java -jar target/studypilot.jar course list
java -jar target/studypilot.jar material import samples/design-notes.md --course COURSE_ID
java -jar target/studypilot.jar material list --course COURSE_ID
```

Replace COURSE_ID with the ID printed by course creation. TXT and Markdown imports
keep original line ranges. Long text is split into overlapping chunks without
breaking Unicode characters. Files over 20 MiB are rejected. Duplicate files
within a course are skipped with a message, and failed imports do not add materials.

PDF imports extract text separately from each page and retain its original page
number. Scanned PDFs with no extractable text are rejected; OCR is outside the scope.

## AI summaries

```sh
java -jar target/studypilot.jar summarize MATERIAL_ID --course COURSE_ID --style brief
```

The styles are `brief`, `detailed`, and `bullet`. A failed or empty model reply is
retried once. Long materials use batches of 20 chunks and a final merge, up to 20
batches. The saved summary is updated only after the complete operation succeeds.
API requests have a 90-second timeout. Provider errors do not print response bodies
or credentials. Available token counts are saved in `llm-usage.jsonl` under DATA_DIR;
the file contains no prompts, responses, or credentials. Billing costs are recorded separately.

`LLM_REASONING_EFFORT` defaults to `low` for the selected model. Leave its value
empty if a different compatible provider does not support that request parameter.

## Concept extraction

```sh
java -jar target/studypilot.jar concepts extract MATERIAL_ID --course COURSE_ID
java -jar target/studypilot.jar concepts list --course COURSE_ID
```

Concepts have names, definitions, optional parent names and source chunk IDs.
The program validates model JSON and source IDs, merges duplicate names, and
removes cyclic or unknown parent links. Invalid responses are retried once.
An empty result preserves earlier concepts. Up to 400 chunks are processed in
batches of 20.

## Questions with citations

```sh
java -jar target/studypilot.jar ask "What does the Observer pattern do?" --course COURSE_ID
```

`RETRIEVAL_MODE=tfidf` selects TF-IDF cosine scoring; `keyword` selects keyword
matching. Both return at most five matching chunks. No model call is made when
retrieval finds no matching chunks. Unknown citation IDs are removed; a reply with
no valid citation is retried once and then reported as a failure. Ambiguous
questions can return a clearly labeled clarification request.

Citation checking establishes that a chunk was retrieved. It does not prove that
every sentence of the answer is supported. Retrieval is lexical and does not use
an embedding model. Semantic answer validation remains part of Stage 3.

## Implementation approach

Plain Java and Swing follow the Stage 1 design. JSON repositories use explicit
field mapping. PDFBox handles PDF text extraction. Tests use JUnit 4.
The implemented patterns are Facade, Strategy, Adapter, Factory Method and Observer.

To follow the code, start with a single operation such as importing a material:

1. `CLI.materialCommand` or `SwingGUI.StudyWorker` receives the input.
2. `AgentFacade` calls `CourseService`, which checks the file and creates chunks.
3. `CourseRepository` converts each object to JSON and saves the course.

The other features follow the same route through the facade and their service.
Model calls use `OpenAIAdapter`; model output is checked by `ResponseParser` and
the relevant service before it is saved. SwingWorker keeps slow file and model
operations from blocking the GUI.

## Development records

See [the development log](docs/development-log.md) for project decisions and checks,
and [the task board](docs/stage2-tasks.md) for the remaining work.

## Flashcards and review

```sh
java -jar target/studypilot.jar flashcards generate MATERIAL_ID --course COURSE_ID --count 5
java -jar target/studypilot.jar review COURSE_ID
java -jar target/studypilot.jar flashcards show CARD_ID --course COURSE_ID
java -jar target/studypilot.jar review COURSE_ID --card CARD_ID --remembered true
java -jar target/studypilot.jar progress --course COURSE_ID
java -jar target/studypilot.jar history --course COURSE_ID
```

Replace MATERIAL_ID and CARD_ID with IDs printed by the application. Generation
accepts 1-50 cards and materials with up to 400 chunks. Duplicate fronts are removed
and the actual number of new cards is reported. The GUI has a Flip card button;
Remembered and Not yet save the review. The CLI shows the back with `flashcards show`
and records a review with `review --card ... --remembered true|false`.

Never-reviewed cards come first, followed by low-mastery cards. Mastery is the
remembered count divided by the review count. Review statistics do not rank weak
quiz topics. ProgressTracker notifies HistoryRecorder and the GUI's DashboardView.
Review counts, card mastery, and history are restored after restarting the app.

## Quiz generation

```sh
java -jar target/studypilot.jar quiz create --course COURSE_ID --topic "Observer" --count 3 --difficulty medium --types multiple-choice,true-false,short-answer
java -jar target/studypilot.jar quiz list --course COURSE_ID
java -jar target/studypilot.jar quiz show QUIZ_ID --course COURSE_ID
```

The Quizzes tab offers the same topic, count, difficulty and type choices, plus a
saved-quiz selector. Suggested is the default difficulty choice; a specific level
overrides it. Request 1-50 questions and at least one per selected type. Counts are balanced
across the selected types. QuizService retrieves up to five relevant chunks and
sends them to the model. No matching material means no model call.

QuestionCreator.buildQuestion checks common fields and calls the factory method
implemented by MCQQuestionCreator, TrueFalseQuestionCreator or
ShortAnswerQuestionCreator. Multiple-choice options must be distinct and contain
the correct answer. True/false answers must be unambiguous, and short answers need
a model answer and a rubric. Malformed and repeated questions are discarded.
The service keeps valid questions and makes at most two additional requests to
replace missing ones. It reports the actual count, which may be smaller than requested.

Quizzes are saved under `quizzes/QUIZ_ID.json`. Saved content cannot be overwritten;
a newly generated quiz gets a new ID. Reading it after restart preserves the
original question IDs, types, answers and rubrics. The displayed questions omit
the answer key.

## Suggested difficulty

```sh
java -jar target/studypilot.jar quiz difficulty --course COURSE_ID --topic "Observer"
```

The suggestion uses graded quiz answers for the exact saved concept name, shown as
Topic beside each answer result. Names ignore case and outer spaces. Different
concepts and courses are kept separate. PENDING answers and flashcard reviews do
not contribute. Accuracy is fully correct answers divided by graded answers. With
fewer than five graded answers, the level is Medium. Otherwise accuracy below 50%
gives Easy, accuracy at or above 80% gives Hard, and the rest gives Medium. The output explains the level and sample count; with no results it says so.

The query needs no API key, makes no model call and changes no saved data. The GUI's
Show suggestion button shows the same level and reason. Quiz creation uses the
suggestion unless a level is selected in the GUI or supplied with `--difficulty` in
the CLI. An invalid CLI difficulty value prints a warning and uses the suggestion.

## Quiz attempts and grading

```sh
java -jar target/studypilot.jar quiz take QUIZ_ID --course COURSE_ID
java -jar target/studypilot.jar quiz submit ATTEMPT_ID --course COURSE_ID --answers data/answers.json
java -jar target/studypilot.jar quiz attempt ATTEMPT_ID --course COURSE_ID
java -jar target/studypilot.jar quiz attempts --course COURSE_ID
```

`quiz take` creates a new attempt every time and prints the question IDs. Put those
IDs in an answers file, replacing the placeholders below:

```json
{"answers":{"QUESTION_ID_1":"1","QUESTION_ID_2":"true","QUESTION_ID_3":"My short answer"}}
```

Use the displayed 1-based option number for multiple choice and `true` or `false`
for true/false. Omitted or blank answers score zero. Objective grading reads the
saved answer key and needs no API key. Each nonempty short answer makes one model
call using the saved question, model answer and rubric. A grade is accepted only
when it contains an integer within the question's point range and a nonempty
explanation. A service failure or invalid reply leaves that answer PENDING.

The Quizzes tab has Start attempt, an answer table, Submit answers and a saved
attempt selector. Submitted answers cannot be edited; start a new attempt to retake
the quiz. Repeating a submission returns its original answers, time and results.
It does not grade again or add duplicate progress/history entries.

Scores use only graded points and show the pending count separately. An attempt
with no graded answers shows an unavailable score. Progress counts submitted
attempts, but PENDING results do not enter graded-answer or topic statistics.
Attempt and result IDs preserve these counts after restarting. Attempts are stored
under `attempts/ATTEMPT_ID.json`; the original quiz is never changed by grading.

To resolve pending short answers:

```sh
java -jar target/studypilot.jar quiz regrade ATTEMPT_ID --course COURSE_ID
java -jar target/studypilot.jar quiz model-answer ATTEMPT_ID QUESTION_ID --course COURSE_ID
java -jar target/studypilot.jar quiz selfgrade ATTEMPT_ID QUESTION_ID right --course COURSE_ID
```

`quiz attempt` prints pending question IDs. `regrade` calls the model once for each
pending answer and leaves earlier grades unchanged. With no pending answers, it
reports that there is nothing to grade and makes no model call. `model-answer`
shows the saved model answer, rubric and your answer. Compare them, then use
`selfgrade` with `right` for full points or `wrong` for zero. Self-assessment needs
no API key and is labeled in the explanation. An already graded answer cannot be
self-graded again.

The GUI offers Retry pending grading and Self-grade selected answer. Select a
pending short-answer row to read the comparison and choose Correct, Incorrect or
Cancel. Newly resolved results update progress once; the original answers and
submission time stay unchanged. Model grading can still make semantic mistakes;
its quality will be evaluated in Stage 3.

## Weak-topic reports

```sh
java -jar target/studypilot.jar weak-topics --course COURSE_ID
```

The Progress tab's Weak topics button runs the same report. Topics are ranked by
fully correct answers divided by graded answers, lowest accuracy first; tied names
are ordered alphabetically. PENDING answers and flashcard mastery do not count.
Topics with too few stored graded results appear under Insufficient data. With no
eligible topics, the report says there is not enough data and makes no model call.

`WEAK_TOPIC_MIN_RESULTS` defaults to 5 in local configuration and must be a positive
integer. This setting controls weak-topic reports; difficulty suggestions still
require five graded answers. Topic names come from stored quiz results, so a topic
with no graded results cannot be ranked.

For an eligible ranking, the model makes one brief explanation from the computed
counts. If the reply fails or is empty, the ranking stays visible and the report
says the AI explanation is unavailable. The report is not saved and does not change study records;
a real model call may append its token counts to the existing usage log. The Java
code fixes the numbers and order. AI advice still needs semantic evaluation in
Stage 3.

## Storage and verification

Runtime files are under DATA_DIR, which defaults to the ignored `data` folder.
Courses use `courses/COURSE_ID.json`; review progress and history use `progress.json`
and `history.json`. Unreadable progress/history is moved to a unique `.bak-*` file,
then starts empty with a warning. Individual JSON saves use temporary-file
replacement. Use one writable application instance per data directory.

```sh
./mvnw clean verify
```

The default tests use temporary directories and a test-only MockLLMClient. They do
not call a paid API. HTTP adapter tests use a local HTTP server. Test doubles are
excluded from the application JAR. Live checks of summaries, concepts, cited
answers and card generation are recorded separately in the development log.

Full Stage 3 deterministic testing and KUMA evaluation, the final seven-section
report, updated UML and feature recordings remain in progress. The quiz GUI has
compiled; its interactive walkthrough is still pending.

## Stage 2 tracking

- [Task board](docs/stage2-tasks.md)
- [Period 1 code commits](docs/period-1-commits.md)
- [Period 2 code commits](docs/period-2-commits.md)
- [Implementation working record](docs/implementation-working-record.md)
- [Usage checkpoint](docs/usage-checkpoint.json)

The report notes will be used to prepare stage2-report.docx and stage2-report.pdf.
