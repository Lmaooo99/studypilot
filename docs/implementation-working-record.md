# Stage 2 Implementation Working Record

Notes for the Stage 2 implementation report, covering the current implementation.

## 1 Project overview and status

StudyPilot helps students study their own course materials. F01-F05 are implemented
with basic automated and live API verification. F11 currently covers review activity
and history. The other original features remain explicit later-period tasks.
The app uses Java 17, Swing, a CLI, JSON repositories, and an OpenAI-compatible
Chat Completions service with configured model ID gpt-6.1-sol.

## 2 Feature to implementation mapping

| Feature | Type | Status | Main files and responsibilities | Key methods | Patterns | Video |
|---|---|---|---|---|---|---|
| F01 Course/material import | Deterministic | Implemented | CourseService validates/extracts/chunks; CourseRepository saves source locations | createCourse, importMaterial, extractPdf | Facade | A, pending |
| F02 Summaries | AI | Implemented | SummaryService batches/merges/saves; OpenAIAdapter translates model calls | summarize, summaryPrompt, mergeSummaryPrompt, generate | Facade, Adapter | A, pending |
| F03 Concepts | Hybrid | Implemented | ConceptService merges and checks relationships; ResponseParser validates structured fields | extractConcepts, parseConcepts | Facade, Adapter | A, pending |
| F04 Cited answers | Hybrid | Implemented | QuestionAnswerService retrieves; strategies score chunks; CitationValidator filters IDs | answer, retrieve, parseCitedAnswer, validate | Facade, Strategy, Adapter | A, pending |
| F05 Flashcards/review | Hybrid | Implemented | FlashcardService generates/reviews; Flashcard stores mastery; tracker notifies observers | generateCards, review, recordReview, getMastery | Facade, Adapter, Observer | A, pending |
| F06 Quiz generation | Hybrid | Not implemented | Planned QuizService, QuestionCreator and typed products | Planned createQuiz/buildQuestion | Planned Factory Method | B, pending |
| F07 Adaptive difficulty | Deterministic | Not implemented | Planned quiz rules and graded statistics | Planned suggestDifficulty | Planned Facade | B, pending |
| F08 Attempts/grading | Hybrid | Not implemented | Planned attempts, grading strategies and pending handling | Planned submitAttempt/retryPendingGrading/selfGrade | Planned Strategy | B, pending |
| F09 Weak topics | Hybrid | Not implemented | Planned WeakTopicAnalyzer and graded statistics | Planned analyze | Planned Adapter | B, pending |
| F10 Study plans | Hybrid | Not implemented | Planned StudyPlanGenerator, StudyPlan and StudyTask | Planned generate/validatePlan/markTaskDone | Planned Observer | C, pending |
| F11 Progress/history | Deterministic | Partial | ProgressTracker saves review counts; HistoryRecorder saves events; DashboardView observes updates | recordReview, getSummary, update, getHistory | Facade, Observer | B, pending |
| F12 Natural-language agent | AI | Not implemented | Planned controller, planner, six tools and memory | Planned handleRequest/createPlan/execute | Planned Facade/Adapter | C, pending |

F01 starts at the GUI/CLI, passes through AgentFacade to CourseService, and saves a
Course with Material and Chunk objects. F02/F03/F05 send material chunks through
PromptTemplates and LLMClient, validate the actual response, then save the result.
F04 retrieves first and checks returned IDs before constructing citations. A review
saves card counts, updates ProgressTracker and notifies HistoryRecorder/DashboardView.
All rows refer to the same feature IDs in the original Stage 1 report; no new feature
has been substituted for an original feature.

## 3 Implemented design patterns

Facade: SwingGUI and CLI call AgentFacade, which delegates to the domain services.
Adapter: OpenAIAdapter implements LLMClient and converts requests/replies through
OpenAIApiClient. Strategy: QuestionAnswerService holds RetrievalStrategy; keyword and
TF-IDF implementations are selected by local configuration. Observer: ProgressTracker
extends Subject; HistoryRecorder and DashboardView implement Observer and react to
actual review events. Factory Method will be implemented with typed quizzes in C08.
The updated implementation UML remains a later delivery task.

## 4 Changes and rationale

Original flat extraction -> per-page PDF chunks -> preserve real page citations -> F01/CourseService/SD01.
Implicit course scope -> explicit CLI --course -> avoid accessing the wrong course -> affected CLI use-case triggers.
Original prompt helpers -> added mergeSummaryPrompt -> support long-material summaries -> F02/PromptTemplates.
CitedAnswer -> optional clarification flag -> represent an ambiguous question without unsupported citations -> F04.
StudyEvent -> eventId -> avoid duplicate observer-history entries -> F05/F11.
DashboardView -> reads through AgentFacade -> retain the common UI entry point while observing progress -> F11.
Startup/configuration -> AppConfig and JsonFiles helpers -> keep credentials local and JSON saves simple -> infrastructure.
The planned courseId/stepId agent signature changes still require implementation and diagram updates.
No pattern was replaced and no additional product feature was added in Period 1.

CourseRepository uses short private methods for each object's JSON mapping.
Answer.hasValidCitation reads its citation list directly instead of keeping a
duplicate boolean field. Flashcard review ordering uses a plain insertion loop.
CLI has one method per command group. SwingGUI uses ActionListener and one private
StudyWorker. These private implementation details must be reflected in the later
updated class diagram.

## 5 AI tools and collaboration

Tool: Codex, using gpt-6.1-sol. AI support covered code generation, test preparation,
debugging and refactoring. Model and token records are in usage-checkpoint.json;
billing records are not yet available.

My work covered the project scope and design choices, API/model selection and
connection setup. I read and reviewed the implementation, integrated the parts,
and validated the results.

Representative interaction:

- Task: implement F01-F05 using the Stage 1 design.
- Input: follow the planned class responsibilities and connect the GUI and CLI to the services.
- AI contribution: generated implementation code, tests and troubleshooting suggestions.
- My contribution: reviewed class responsibilities and feature flow, checked integration, and validated the resulting implementation.
- Outcome: material imports, summaries, concepts, cited answers and flashcard review were implemented and checked.

## 6 Development and verification

The complete implementation passed 52 JUnit tests with Java 17. The packaged CLI
imported sample material and reloaded it in another process; a summary request
without API settings returned a missing-key error.

Real API checks produced a summary, 8 concepts, a cited answer and 3
flashcards. Review counts and history were restored after restarting. GUI checks
covered startup, a real summary, busy/ready controls and an empty course name.
The final GUI walkthrough and demonstration recordings remain to be completed.

The latest CLI check recorded two reviews, mastery 0.5 and two history events,
then loaded the same values in new processes. Course isolation, rejected review
values and backups of damaged progress/history files were also checked.

Important fixes included preserving complete Unicode characters in chunks,
rejecting files containing only an encoding marker, checking citation IDs,
preserving saved summaries after a failed request and avoiding duplicate history
entries. Test doubles are limited to the test directory.

Current limits include no OCR, keyword/TF-IDF retrieval, and citation checking based
on retrieved IDs. This check does not prove that every answer sentence is supported.
The application uses one writer per data directory; separate JSON files are not
updated as a single transaction. Quiz and task statistics, later features, updated
UML and Stage 3 evaluation remain on the task board.

## 7 Demonstration index

- Recording A: F01-F05.
- Recording B: F06-F09 and F11.
- Recording C: F10 and F12.

The recordings will show inputs, running output and AI involvement through the GUI
and CLI. Video links and timestamps will be added after recording and upload.
