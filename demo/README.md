# Demonstration assets

These three silent videos replay commands and output captured from actual
StudyPilot Java 17 CLI processes on November 3, 2026, at 13:52-13:54 EST.
They use [samples/design-notes.md](../samples/design-notes.md). Network waits are
shortened, long output is wrapped and split into labelled continuation pages,
and reading holds are added. Each segment retains the actual run time, exit code
and process duration. Pause the 1080p video to inspect detailed questions or IDs.

The CLI workflow used thirteen actual model responses. The explanatory text
identifies the model's role; local validation, retrieval, statistics and
persistence remain Java operations. These assets are supporting CLI execution
replays. The required native GUI walkthrough and screen recording remain pending.
No complete GUI verification or course submission is claimed.

| Asset | Duration | Coverage |
|---|---|---|
| [A](recording-A-cli.mp4) | 01:20.30 | F01-F05: course/import, summary, concepts, cited answers and flashcard review |
| [B](recording-B-cli.mp4) | 06:50.00 | F06-F09/F11: mixed quizzes, grading, difficulty, weak topics and saved history |
| [C](recording-C-cli.mp4) | 01:37.67 | F10/F12: dated plans, repeated completion and a real dependent agent workflow |

## Feature index

| Stage 1 feature | Asset | Start | Accessible timestamp URL |
|---|---|---|---|
| F01 Course/material import | [A](recording-A-cli.mp4) | 00:00 | Upload link pending |
| F02 Summaries | [A](recording-A-cli.mp4) | 00:21 | Upload link pending |
| F03 Concepts | [A](recording-A-cli.mp4) | 00:28 | Upload link pending |
| F04 Cited answers | [A](recording-A-cli.mp4) | 00:38 | Upload link pending |
| F05 Flashcards/review | [A](recording-A-cli.mp4) | 00:52 | Upload link pending |
| F06 Quiz generation | [B](recording-B-cli.mp4) | 00:00 | Upload link pending |
| F07 Adaptive difficulty | [B](recording-B-cli.mp4) | 06:20 | Upload link pending |
| F08 Attempts/grading | [B](recording-B-cli.mp4) | 00:46 | Upload link pending |
| F09 Weak topics | [B](recording-B-cli.mp4) | 06:27 | Upload link pending |
| F10 Study plans | [C](recording-C-cli.mp4) | 00:00 | Upload link pending |
| F11 Progress/history | [B](recording-B-cli.mp4) | 06:34 | Upload link pending |
| F12 Natural-language agent | [C](recording-C-cli.mp4) | 00:48 | Upload link pending |

## What the executions show

A includes a rejected duplicate import and an unrelated question returning without
a model request. Saved card mastery is read again in a separate JVM.

B includes all three question types, an invalid question count, a malformed answers
file rejected before submission, objective grades with pending short answers, and
a real grading retry. Repeated submission preserves the original attempt. The
final difficulty suggestion and weak-topic report use the saved graded results.

C includes invalid-date rejection, a saved plan reopened after completion, and an
agent plan that runs progress, weakTopics and quiz. Its trace shows `$step2.topic`
resolving to the measured topic before quiz creation. A later process reuses the
saved session. A missing request is rejected before planning.

The first attempt's verification answers came from the saved model answers to
exercise grading. Four later attempts intentionally submitted empty answer maps
to create weak-topic history. Their resulting accuracy is demonstration data,
not student assessment. The malformed answers wrapper was corrected after the
application rejected it. These inputs do not establish semantic grading accuracy.

## Remaining recording and upload work

Use the [GUI walkthrough guide](gui-walkthrough.md) to record the actual native
application, including text input, selected controls, output and restarts. The
guide is a preparation script, not a completed test or recording.

After the GUI recording and student review, the student uploads the videos to an
accessible host. Replace the pending entries here and in the Word/PDF report with
real timestamp links, then check access as a viewer. The student submits the
repository URL through the course system. Keep `.env` and runtime JSON files local.
