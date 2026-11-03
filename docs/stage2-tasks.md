# Stage 2 Task Board

The original Stage1-Report.pdf remains the design baseline.
This board describes the implementation. Code commits are listed in
period-1-commits.md, period-2-commits.md and period-3-commits.md.

## Implementation periods

- Period 1: October 20-26, 2026. Target seven meaningful code commits.
- Period 2: October 27-November 2, 2026. Target seven meaningful code commits.
- Period 3: November 3-9, 2026. Target seven meaningful code commits.
- All dates are Toronto local time. Commit and push during the applicable period.
- Documentation-only commits do not count toward the code-commit target.

| Period | Task | Work | Status |
|---|---|---|---|
| 1 | C01 | Application startup and safe local configuration | Implemented |
| 1 | C02 | TXT/Markdown import and line provenance | Implemented |
| 1 | C03 | PDF import and page provenance | Implemented |
| 1 | C04 | LLM adapter and material summaries | Implemented |
| 1 | C05 | Concept extraction and validation | Implemented |
| 1 | C06 | Retrieved answers and citation validation | Implemented |
| 1 | C07 | Flashcards, reviews and Observer notifications | Implemented |
| 2 | C08 | Mixed quizzes and Factory Method | Implemented |
| 2 | C09 | Attempts and objective grading | Implemented |
| 2 | C10 | Short-answer grading and pending results | Implemented |
| 2 | C11 | Adaptive difficulty | Implemented |
| 2 | C12 | Weak-topic ranking and explanation | Implemented |
| 2 | C13 | Validated study plans and task completion | Implemented |
| 2 | C14 | Agent tools, planning, memory and trace | Implemented core |
| 3 | C15 | Step references, validation and bounded recovery | Implemented |
| 3 | C16 | Complete progress/history and duplicate-count prevention | Implemented |
| 3 | C17 | Persistence integration tests | Implemented |
| 3 | C18 | Grading/difficulty/plan integration tests | Implemented |
| 3 | C19 | Agent recovery and session-isolation tests | Implemented |
| 3 | C20 | CLI and structured-result acceptance tests | Implemented |
| 3 | C21 | Complete workflows across restarts | Implemented |

Material imports preserve complete Unicode characters at chunk boundaries and
reject files containing only an encoding marker.

## Remaining delivery work

- Complete the native GUI walkthrough and real GUI recording.
- Verify the final published source and assets from a fresh GitHub clone.
- Add uploaded, accessible timestamp links for every Stage 1 feature.
- Maintain changes and rationale, representative AI/human interactions, token/money records and weekly commit links.
- CLI replay A covers F01-F05, B covers F06-F09/F11, and C covers F10/F12. A native GUI recording is still required.
- Video uploads, timestamp-link checks and course-system submission remain pending.
- Complete the final GUI walkthrough and review later code changes. The confirmed review covers F01-F05 and review/history; review of the Period 2 and Period 3 code remains pending.
- Add development-tool and API billing records when available.
- Complete the clean-clone rehearsal and final checks by November 9; final submission is due November 10.

## Fixed implementation decisions

- Plain Java 17, Swing, Maven Wrapper, JSON files and JUnit 4.
- OpenAI-compatible Chat Completions, configured model gpt-6.1-sol. Credentials remain in ignored .env.
- AgentFacade is the GUI/CLI entry point. Five patterns: Facade, Strategy, Adapter, Factory Method and Observer.
- Agent calls six tools and scopes operations by courseId. C15 resolves whole-value references such as $step1.topic from actual successful results.
- Each plan permits eight steps, each failed action at most two extra agent retries, and each request at most two replans. Input errors replan; insufficient data clarifies.
- Successful IDs/arguments stay fixed across replans. Identical resolved actions reuse results and retain exhausted retry budgets. Earlier outputs and errors remain available.
- PENDING grading never contributes to graded accuracy, weak-topic ranking or difficulty.
- Difficulty needs five graded results: Easy below 50%, Hard at 80% or above, otherwise Medium. Weak-topic reports and plans use the configured minimum, default five.
- Plans accept 1-24 whole hours per day, validate dates and daily minutes, and allow three proposal attempts. Insufficient graded history requires a diagnostic quiz first.
- MockLLMClient is limited to tests. Stage 3 covers the full deterministic and KUMA evaluation.

## Period 1 release verification

- The complete implementation passed 52 JUnit tests with Java 17.
- Packaged CLI import and fresh-process reload passed without a real .env file.
- A missing local key produces an explicit failure rather than generated sample output.
- Real API checks produced a summary, 8 concepts, a cited answer and 3 flashcards.
- Review/mastery/history survived restart. Native GUI Summary returned a real model result.
- I reviewed, integrated and validated the Period 1 implementation. The final GUI walkthrough and recordings remain pending.

## Period 2 checks so far

- C08 adds mixed quizzes with the three Stage 1 question creators, bounded replacement and immutable JSON storage.
- The final Period 2 Java 17 build, `./mvnw -q clean verify`, passed 202 tests.
- A real API response produced three questions, one of each type. CLI list/show loaded the same IDs and content in new processes without credentials.
- Course isolation, missing credentials, unrelated topics and invalid counts were checked without changing the saved quiz.
- The Quizzes tab compiles. Its interactive walkthrough and student review remain pending.
- C09 adds saved attempts, objective grading, blank-answer handling and pending short answers. C10 adds rubric grading, pending retries and self-assessment.
- Separate CLI processes confirmed two submitted attempts, four graded answers, three correct answers and two history events. A repeated submission preserved the first result and timestamp.
- Objective grading did not change the saved quiz or require an API key. Saved attempt/result IDs prevent duplicate quiz and topic counts.
- C10 checked a real grading reply: one pending short answer became graded after one API call. A second attempt was resolved by self-assessment without an API key.
- Across separate CLI processes, the two attempts had six graded answers, five correct answers and four history events. Repeated actions preserved answers, submission times and counts.
- Tests reject invalid, fractional, coerced and out-of-range grades. A failed history save can be retried without another model call or duplicate counts.
- C11 adds exact-topic difficulty suggestions, the five-result minimum, accuracy thresholds, reasons and explicit overrides.
- CLI checks observed Medium at four graded answers, Hard at 80%, Medium at 50%, and Easy below 50%, across separate processes. Suggestion queries changed no files and used no API key.
- C12 ranks eligible topics from graded quiz results and lists smaller samples separately. Its configurable minimum defaults to five.
- A real explanation call used three topic counts. Missing credentials and insufficient history kept the computed report available without invented advice. Study records were unchanged.
- C13 adds validated study plans and idempotent task completion through the existing Observer.
- A real plan reply produced six dated tasks within three daily 60-minute budgets. CLI checks restored IDs/flags, rejected invalid input and course access, and retained one completion after repeated actions. GUI interaction and student review remain pending.
- C14 connects the six Stage 1 tools, structured planning, saved course-bound messages, actual results and trace output through GUI/CLI entry points.
- C14 tests covered the eight-step limit, tool/argument checks, partial failure, no-history clarification, memory persistence and JSON output. Step references and retry/replan recovery were added in C15.
- Live CLI checks verified progress plus summary, a saved-context follow-up and no-history clarification. The earlier summary timeout was preserved as a partial-failure check; its unavailable usage is recorded separately.

## Period 3 checks so far

- C15 passed 224 JUnit tests with Java 17. Controlled test tools counted retries/replans, checked dependency ordering and prevented repeated completed actions.
- A real dependency-chain check used weakTopics then quiz with the actual returned topic field. The separate verification course had five intentionally incorrect CLI attempts on one real generated question.
- A saved-session follow-up restored five submitted attempts in a new process. No-history input clarified, and offline CLI checks rejected cross-course sessions, duplicate flags and missing credentials.
- C15 corrected the C14 commit row and stale courseId/stepId note. C21 adds the complete integration workflows and final diagrams/report; native GUI verification and recording remain pending.

- C16 passed 245 JUnit tests and 26 packaged CLI checks without API credentials or new model calls.
- Progress/history writes no longer expose unsaved in-memory counts/events. Saved course review events permit recovery after a later write fails.
- New-process checks restored two reviews, five submitted attempts, five graded answers, one completed task and eight history entries. Healthy reads and repeated completion changed no files.
- Damaged progress/history bytes were retained in two backups before rebuilding. Missing or damaged source records kept existing counts and produced warnings. Unavailable history times remain explicitly labelled snapshots.

- C17 passed 262 JUnit tests, including 17 new material/quiz persistence tests, plus 29 packaged CLI checks without API credentials.
- Fresh facades retained source IDs, line/page citations, summaries, concepts, reviewed cards and all three question types. Deleted original input files did not prevent reopening saved work.
- Storage checks now reject coerced numeric fields, duplicate material/chunk IDs and conflicting source locations without changing the damaged files.
- CLI checks imported TXT, Markdown and a 46-page PDF, retained 103 PDF chunks, and submitted answers using the authored verification quiz's original question IDs. No new model call was made.

- C18 passed 276 JUnit tests, including 14 new grading/difficulty/plan integration checks, and 46 offline packaged CLI checks.
- Pending resolutions changed only graded counts and later feedback: the checks reached 40%, exactly 50% and exactly 80% with the expected difficulty levels. Partial credit and fully correct-answer accuracy remained distinct.
- Plans used restored graded-topic data, retained earlier schedules, required diagnostics without enough graded evidence, enforced date/budget corrections and counted repeated completion once.
- Saved grading scores and flags now reject coerced JSON values without changing the input. CLI missing-configuration checks exercised PENDING and plan failure before any provider request; authored fixtures are labelled as verification inputs.

- C19 passed 291 JUnit tests, including 15 new service-backed agent and conversation integration checks, and 28 offline packaged CLI checks.
- Real service/repository paths retained saved summaries through bounded recovery and later failures, corrected quiz input before saving, and kept request-specific result references separate.
- Course/session switches retained their own messages and counts. Planning used the last ten messages while storage retained all turns; invalid or damaged conversations stopped before planning without losing source data.
- CLI checks used authored earlier context and actual missing-configuration failures. Failed turns survived restart, cross-course access was rejected, and blocked storage was preserved. No new provider request or production-code change was required.

- C20 passed 304 JUnit tests, including 13 new CLI/input/JSON tests, and 33 offline packaged CLI checks.
- Structured results retained actual partial and recovered outcomes, references, retry metadata and escaped text. Clarification returned zero; failures returned one; warnings stayed on stderr.
- A new test exposed known agent options being accepted in place of a request. CLI.agentCommand now rejects those inputs with code two before planning or saving a turn, while option names within ordinary request text remain valid.
- Packaged CLI checks verified the guard, option ordering, invalid integer/date inputs, one-object failure JSON, saved request text and damaged-progress backups without a provider request. Native GUI and student review remain pending.

- C21 passed 307 JUnit tests, including three complete workflows across fresh application instances.
- A complete study flow retained source IDs, summaries, concepts, review mastery, mixed questions, submitted answers, grading, task completion and a course-bound agent conversation after restart.
- Removing only derived progress/history files in a test directory rebuilt supported counts and labelled snapshots without inventing lost grading events. Later generation failures preserved earlier saved work.
- The production CLI workflow used 13 actual API responses. A real agent resolved the weakTopics result into a new quiz and reopened the same conversation in another JVM.
- Demonstration data reached one review, five submitted attempts, 30 graded answers, six correct answers and one completed task. Four empty submissions deliberately created weak-topic history; these are not student study results.
- Updated UMLet sources and PNG exports are in docs/uml. The report accounts for all twelve features, five patterns, design changes, collaboration, usage and the three reporting periods. Native GUI text entry was not reliable during the automated check; the full GUI walkthrough and recording remain pending.
