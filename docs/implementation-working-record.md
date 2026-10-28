# Stage 2 Implementation Working Record

Notes for the Stage 2 implementation report, covering the current implementation.

## 1 Project overview and status

StudyPilot helps students study their own course materials. F01-F09 are implemented
with basic automated and live API verification. F08 supports saved attempts,
objective and rubric grading, pending retries and self-assessment. F11 covers reviews,
quiz submissions, graded-answer counts and history. The other original features
remain explicit later-period tasks.
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
| F06 Quiz generation | Hybrid | Implemented | QuizService retrieves/generates; ResponseParser reads drafts; QuestionCreator and three subclasses validate typed products; QuizRepository saves immutable quizzes | createQuiz, quizPrompt, parseRawQuestions, buildQuestion, createQuestion, saveQuiz, loadQuiz | Facade, Strategy, Adapter, Factory Method | B, pending |
| F07 Adaptive difficulty | Deterministic | Implemented | QuizService chooses a level; ProgressTracker reads course/topic graded statistics; DifficultySuggestion holds the level, accuracy, count and reason; GUI/CLI accept an override | suggestDifficulty, chooseDifficulty, getAccuracy, getGradedCount | Facade | B, pending |
| F08 Attempts/grading | Hybrid | Implemented | QuizAttempt preserves submitted answers; QuizGrader selects ExactMatchGrading or LLMRubricGrading; ResponseParser checks grades; QuizService resolves PENDING; QuizRepository restores attempts | startAttempt, submitAttempt, grade, parseGrading, regradePending, retryPendingGrading, getPendingQuestion, selfGrade, replacePendingResult | Facade, Strategy, Adapter, Observer | B, pending |
| F09 Weak topics | Hybrid | Implemented | ProgressTracker filters and sorts graded ConceptStats; WeakTopicAnalyzer requests a brief explanation; WeakTopicReport retains ranking and insufficient-data names on failure | getWeakTopics, analyze, getRankedStats, weakTopicPrompt | Facade, Adapter | B, pending |
| F10 Study plans | Hybrid | Not implemented | Planned StudyPlanGenerator, StudyPlan and StudyTask | Planned generate/validatePlan/markTaskDone | Planned Observer | C, pending |
| F11 Progress/history | Deterministic | Partial | ProgressTracker records reviews and quiz submissions; ProgressSummary/ConceptStats count graded results once; HistoryRecorder saves events; DashboardView observes updates | recordReview, recordQuizAttempt, getSummary, update, getHistory | Facade, Observer | B, pending |
| F12 Natural-language agent | AI | Not implemented | Planned controller, planner, six tools and memory | Planned handleRequest/createPlan/execute | Planned Facade/Adapter | C, pending |

F01 starts at the GUI/CLI, passes through AgentFacade to CourseService, and saves a
Course with Material and Chunk objects. F02/F03/F05 send material chunks through
PromptTemplates and LLMClient, validate the actual response, then save the result.
F04 retrieves first and checks returned IDs before constructing citations. A review
saves card counts, updates ProgressTracker and notifies HistoryRecorder/DashboardView.
All rows refer to the same feature IDs in the original Stage 1 report; no new feature
has been substituted for an original feature.

F06 passes the GUI/CLI request through AgentFacade to QuizService. RetrievalStrategy
selects chunks, quizPrompt supplies the context, and LLMClient returns drafts.
ResponseParser reads them; the matching QuestionCreator builds each valid product.
QuizRepository saves the Quiz and the GUI/CLI shows its questions and actual count.

F08 starts a saved QuizAttempt through AgentFacade/QuizService. Submission validates
answer IDs, then QuizGrader chooses ExactMatchGrading for objective answers or
LLMRubricGrading for nonempty short answers. Missing answers score zero. The rubric
strategy calls the model once with the saved answer and rubric; ResponseParser
checks the integer score and explanation. Invalid or failed grades remain PENDING.
The saved attempt passes to ProgressTracker, which persists graded-only statistics
and notifies the observers. Repeating a submission reuses its answers and grades.

A pending retry loads the same attempt and grades only its pending answers. For
self-assessment, getPendingQuestion returns the saved model answer and rubric;
selfGrade records full or zero points with an explicit self-assessment explanation.
Only a pending short answer can change. Newly graded results are counted once,
while the original answer map and submission timestamp stay unchanged.

F07 passes the course and topic through AgentFacade to QuizService.suggestDifficulty.
ProgressTracker reads graded counts and accuracy from the course's ProgressSummary.
chooseDifficulty applies the minimum sample and accuracy rules; DifficultySuggestion
returns the level and reason. The GUI/CLI shows the suggestion and passes the chosen
level into F06. Reading a suggestion makes no model call and saves nothing.

F09 starts at the Progress tab or weak-topics CLI command. AgentFacade checks the
course and calls WeakTopicAnalyzer.analyze. ProgressTracker.getRankedStats returns
eligible topics in ascending accuracy order and copied counts. The analyzer lists
smaller samples separately and passes the eligible data through weakTopicPrompt to
LLMClient. WeakTopicReport always retains the computed ranking, including when the
explanation fails. With no eligible topic, it reports insufficient data without a
model call or a weakest-topic claim.

## 3 Implemented design patterns

Facade: SwingGUI and CLI call AgentFacade, which delegates to the domain services.
Adapter: OpenAIAdapter implements LLMClient and converts requests/replies through
OpenAIApiClient. Strategy: QuestionAnswerService holds RetrievalStrategy; keyword and
TF-IDF implementations are selected by local configuration. Observer: ProgressTracker
extends Subject; HistoryRecorder and DashboardView implement Observer and react to
actual review events. Factory Method uses the abstract QuestionCreator. Its common
buildQuestion operation calls createQuestion, which the three concrete creators
override to construct MultipleChoiceQuestion, TrueFalseQuestion or ShortAnswerQuestion.
QuizService holds the type-to-creator map and does not construct the products itself.
The updated implementation UML remains a later delivery task.

QuizGrader is also a Strategy context and holds GradingStrategy objects.
ExactMatchGrading handles multiple choice and true/false; LLMRubricGrading handles
short answers. Both implement the same grade method. This is the same Strategy
pattern used for retrieval, so it does not add another pattern to the count.

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

For F06, QuizService reuses the configured RetrievalStrategy to select relevant
chunks. This adds a retrieval association to the Stage 1 QuizService design and
affects UC07/SD06. Generation balances selected types, keeps valid drafts and makes
at most two replacement requests. Invalid or repeated drafts are not saved. A quiz
may contain fewer questions than requested; the UI and CLI show both counts.
QuizRepository and plain restore constructors preserve question IDs and types;
saved quizzes cannot be overwritten. listQuizzes/getQuiz/describe support reopening
them. These methods and the retrieval association belong in the updated UML.
F07 now supplies the default difficulty. A valid explicit level overrides it.

F08 adds explicit course scope to startAttempt/submitAttempt and provides
getAttempt/listAttempts for reopening saved work. Objective answers use displayed
option numbers, avoiding ambiguous option text. QuizRepository checks saved results
against their immutable Quiz and preserves submission timestamps. These changes
affect UC09, SD07 and the quiz/persistence class views.

ProgressSummary stores submitted-attempt IDs and attempt/question result keys.
This lets ProgressTracker count retakes separately while counting a retried result
once. Progress is saved before the result's counted flag; stable event IDs prevent
duplicate history. Duplicate notification also retries a failed history save.
Older review-only progress JSON defaults the new fields to empty. Invalid saved
history timestamps are backed up instead of escaping as an unhandled date error.

The Stage 1 statsByConcept map in ProgressTracker is stored inside each course's
ProgressSummary instead. This keeps the topic counts and their saved result keys
together and avoids repeating courseId in every ConceptStats object. quizzesTaken
is derived from saved attempt IDs. The updated progress class view must show this
ownership and the new ID lists.

F08 pending resolution adds ResponseParser.parseGrading, QuizAttempt.getResult and
replacePendingResult, plus a getPendingQuestion helper in QuizService/AgentFacade
for answer/rubric comparison. PromptTemplates keeps the Stage 1 three-string
gradingPrompt signature; its question argument now includes the saved model answer
and maximum points. The GUI adds a status column and a selected-answer comparison
dialog, while CLI adds regrade/model-answer/selfgrade commands. UC10, SD08 and the
grading/UI class views must show these operations. History events use the time of
the grading update; QuizAttempt retains the first submission time.

F07 restores the Stage 1 DifficultySuggestion, suggestDifficulty/chooseDifficulty
and ProgressTracker.getAccuracy/getGradedCount operations. Topic lookup uses the
existing ConceptStats names, with exact matching after trimming and ignoring case.
QuizAttempt.describe now shows those names so students can reuse the right topic.
There is no inference that differently named concepts are equivalent. The GUI adds
a Suggested choice and Show suggestion; CLI quiz create uses the suggestion when
no valid override is supplied. These details affect UC07/UC08, SD06 and the quiz/UI
class views. The feature and its Facade use still match Stage 1.

F09 follows the Stage 1 analyzer/report and SD09 flow. getRankedStats accepts zero
to retrieve tracked topics for the insufficient-data list. Tied accuracies use topic
name order, and returned ConceptStats are copies so later grading cannot change a
report's snapshot. AppConfig adds the positive WEAK_TOPIC_MIN_RESULTS setting,
default 5, to implement the configurable Stage 1 minimum. No storage schema changed.
WeakTopicReport getters and formatting, the CLI command and the Progress tab button
belong in the updated class views and UC11/SD09. No new pattern was added.

## 5 AI tools and collaboration

Tool: Codex, using gpt-6.1-sol. AI support covered code generation, test preparation,
debugging and refactoring. Model and token records are in usage-checkpoint.json;
billing records are not yet available.

My work covered the project scope and design choices, API/model selection and
connection setup. I read and reviewed the implementation, integrated the parts,
and validated the Period 1 results. Review of the Period 2 implementation is pending.

Representative interaction:

- Task: implement F01-F05 using the Stage 1 design.
- Input: follow the planned class responsibilities and connect the GUI and CLI to the services.
- AI contribution: generated implementation code, tests and troubleshooting suggestions.
- My contribution: reviewed class responsibilities and feature flow, checked integration, and validated the resulting implementation.
- Outcome: material imports, summaries, concepts, cited answers and flashcard review were implemented and checked.

Quiz additions used the F06/UC07 and F08/UC09 designs. AI support produced code and
tests for mixed generation, objective/rubric grading, storage and GUI/CLI entry
points. Generation and short-answer grading passed real API checks. Objective
grading, pending self-assessment and repeat-action checks required no model calls.
C11 adds the deterministic difficulty logic, GUI/CLI selection and boundary tests.
C12 adds the weak-topic analyzer/report, ranking rules, fallback and related tests.
My review of these additions remains pending.

## 6 Development and verification

The current implementation passed 147 JUnit tests with Java 17. The packaged CLI
imported sample material and reloaded it in another process; a summary request
without API settings returned a missing-key error.

Real API checks produced a summary, 8 concepts, a cited answer and 3
flashcards. Review counts and history were restored after restarting. GUI checks
covered startup, a real summary, busy/ready controls and an empty course name.
The final GUI walkthrough and demonstration recordings remain to be completed.

The Period 1 review check recorded two reviews, mastery 0.5 and two history events,
then loaded the same values in new processes. Course isolation, rejected review
values and backups of damaged progress/history files were also checked.

The F06 live check generated three questions, one of each type, in one model call.
CLI list/show reloaded the same quiz in new processes without API settings. Tests
and CLI checks covered invalid drafts, replacement limits, partial results, course
isolation, saved question IDs, immutable lists and rejected overwrites. The quiz GUI
compiled; its interactive walkthrough remains pending.

C09 checked fresh attempt IDs, graded-only score denominators, blank answers,
PENDING exclusion and repeated submissions after restart. The packaged CLI used
the earlier real generated quiz, then saved two submissions with four graded
answers, three correct answers and two history events. Course isolation and invalid
answer IDs were checked; the original quiz and first submission were unchanged.
No new API call was needed. A test forced a history-save failure, then confirmed
that resubmission persisted one history event without counting results again.

C10 checked valid and invalid rubric grades, partial points, pending-only retries,
self-assessment, course scope and recovery after a failed history save. A real API
call graded a previously pending answer against its stored model answer and rubric.
The second attempt was self-assessed offline. Separate CLI processes restored six
graded answers, five correct answers and four history events across the two attempts.
No pending answer entered the counts before resolution. Repeated requests left the
original answers, submission times and quiz unchanged. The new GUI controls compile;
their interactive walkthrough is still pending.

C11 tested the five-result minimum, both exact thresholds, pending exclusion,
resolved results, course/topic isolation and restart persistence. CLI generation
tests verified default, valid override and invalid-override fallback using a test
LLM. Packaged CLI checks used saved quiz attempts without credentials: four results
kept Medium, 80% selected Hard, 50% selected Medium and a lower accuracy selected
Easy. Every suggestion read left saved data unchanged and required no model call.
The updated GUI opened, but the interactive walkthrough remains pending because
the native controls could not be operated reliably during this check.

C12 tested ordering, ties, the configurable minimum, pending and review exclusion,
course isolation, snapshots and explanation failure. A real API reply explained
three eligible topics. Separate CLI runs checked missing-key fallback, a higher
minimum, invalid settings and a course with no history. All study records remained
unchanged; only the successful model call added a usage record. The Progress button
compiles; its interactive GUI walkthrough remains pending.

Important fixes included preserving complete Unicode characters in chunks,
rejecting files containing only an encoding marker, checking citation IDs,
preserving saved summaries after a failed request and avoiding duplicate history
entries. Test doubles are limited to the test directory.

Current limits include no OCR, keyword/TF-IDF retrieval, and citation checking based
on retrieved IDs. This check does not prove that every answer sentence is supported.
The application uses one writer per data directory; separate JSON files are not
updated as a single transaction. Task statistics, complete storage recovery, later
features, updated UML and Stage 3 evaluation remain on the task board. A valid model
grade does not prove that its semantic judgment is correct.

## 7 Demonstration index

- Recording A: F01-F05.
- Recording B: F06-F09 and F11.
- Recording C: F10 and F12.

The recordings will show inputs, running output and AI involvement through the GUI
and CLI. Video links and timestamps will be added after recording and upload.
