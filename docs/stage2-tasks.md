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
| 2 | C09 | Attempts and objective grading | Planned |
| 2 | C10 | Short-answer grading and pending results | Planned |
| 2 | C11 | Adaptive difficulty | Planned |
| 2 | C12 | Weak-topic ranking and explanation | Planned |
| 2 | C13 | Validated study plans and task completion | Planned |
| 2 | C14 | Agent tools, planning, memory and trace | Planned |
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

- Complete F07-F10, F12 and the rest of F11 in their periods.
- Update implemented UML classes and affected use cases/SD01/SD13/SD14, retaining UMLet sources and images.
- Produce stage2-report.docx and stage2-report.pdf with all seven required sections.
- Map every Stage 1 feature to actual files, responsibilities, methods, patterns and video timestamps.
- Maintain changes and rationale, representative AI/human interactions, token/money records and weekly commit links.
- Record A (F01-F05), B (F06-F09/F11), and C (F10/F12), collectively showing GUI and CLI.
- Video uploads, timestamp-link checks and course-system submission remain pending.
- Complete the final GUI walkthrough and review later code changes. The confirmed review covers F01-F05 and review/history; review of the new quiz code remains pending.
- Add development-tool and API billing records when available.
- Complete the clean-clone rehearsal and final checks by November 9; final submission is due November 10.

## Fixed implementation decisions

- Plain Java 17, Swing, Maven Wrapper, JSON files and JUnit 4.
- OpenAI-compatible Chat Completions, configured model gpt-6.1-sol. Credentials remain in ignored .env.
- AgentFacade is the GUI/CLI entry point. Five patterns: Facade, Strategy, Adapter, Factory Method and Observer.
- Agent calls six tools, validates stepId references such as $step1.topic and scopes all operations by courseId.
- Maximum eight steps per plan, two extra retries per step and two replans per request.
- Preserve successful tool results during replanning, clarify missing data and report partial completion honestly.
- PENDING grading never contributes to graded accuracy, weak-topic ranking or difficulty.
- Topic statistics use a minimum of five graded results; difficulty is Easy below 50%, Hard at 80% or above, otherwise Medium.
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
- Java 17 `./mvnw -q clean verify` passed 73 tests.
- A real API response produced three questions, one of each type. CLI list/show loaded the same IDs and content in new processes without credentials.
- Course isolation, missing credentials, unrelated topics and invalid counts were checked without changing the saved quiz.
- The Quizzes tab compiles. Its interactive walkthrough and student review remain pending.
