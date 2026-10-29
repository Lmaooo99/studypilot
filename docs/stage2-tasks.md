# Stage 2 Task Board

The original Stage1-Report.pdf remains the design baseline.
This board describes the implementation. Code commits are listed in
period-1-commits.md and period-2-commits.md.

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
| 3 | C15 | Step references, validation and bounded recovery | Planned |
| 3 | C16 | Complete progress/history and duplicate-count prevention | Planned |
| 3 | C17 | Persistence integration tests | Planned |
| 3 | C18 | Grading/difficulty/plan integration tests | Planned |
| 3 | C19 | Agent recovery and session-isolation tests | Planned |
| 3 | C20 | CLI and structured-result acceptance tests | Planned |
| 3 | C21 | Complete workflows across restarts | Planned |

Material imports preserve complete Unicode characters at chunk boundaries and
reject files containing only an encoding marker.

## Remaining delivery work

- Complete F12 recovery/references and the rest of F11 in Period 3.
- Update implemented UML classes and affected use cases/SD01/SD06/SD07/SD08/SD09/SD10/SD11/SD13/SD14, retaining UMLet sources and images.
- Produce stage2-report.docx and stage2-report.pdf with all seven required sections.
- Map every Stage 1 feature to actual files, responsibilities, methods, patterns and video timestamps.
- Maintain changes and rationale, representative AI/human interactions, token/money records and weekly commit links.
- Record A (F01-F05), B (F06-F09/F11), and C (F10/F12), collectively showing GUI and CLI.
- Video uploads, timestamp-link checks and course-system submission remain pending.
- Complete the final GUI walkthrough and review later code changes. The confirmed review covers F01-F05 and review/history; review of the Period 2 code remains pending.
- Add development-tool and API billing records when available.
- Complete the clean-clone rehearsal and final checks by November 9; final submission is due November 10.

## Fixed implementation decisions

- Plain Java 17, Swing, Maven Wrapper, JSON files and JUnit 4.
- OpenAI-compatible Chat Completions, configured model gpt-6.1-sol. Credentials remain in ignored .env.
- AgentFacade is the GUI/CLI entry point. Five patterns: Facade, Strategy, Adapter, Factory Method and Observer.
- Agent calls six tools and scopes operations by courseId. C15 adds validated stepId references such as $step1.topic.
- The eight-step limit is enforced. The two-extra-retry/two-replan agent recovery is scheduled for C15.
- Current execution clarifies missing data and keeps partial results. C15 will preserve successful results during replanning.
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
- The current Java 17 build, `./mvnw -q clean verify`, passed 202 tests.
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
- Tests cover the eight-step limit, tool/argument checks, partial failure, no-history clarification, memory persistence and JSON output. Agent step references and retry/replan recovery remain C15 work.
- Live CLI checks verified progress plus summary, a saved-context follow-up and no-history clarification. The earlier summary timeout was preserved as a partial-failure check; its unavailable usage is recorded separately.
