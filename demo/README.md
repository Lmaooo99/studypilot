# Native demonstration

The student recorded the actual StudyPilot GUI and terminal on November 10, 2026.
The 17:09 master joins all sixteen source clips in their original order without
removing content. It uses the current working-copy Java implementation, including
the November 9 corrections. No application output or narration was synthesized.

Video: [StudyPilot Stage 2 demonstration](https://www.youtube.com/watch?v=eNrlBO0PAuY).

Unlisted playback and all twelve feature plus two CLI timestamp links were verified in a signed-out browser. The 1080p60 HD option is available.

The recorded JAR SHA256 is `c375c84cb4041926dbadde28b56de5c9e3ff9dda8b23046d0821d6bbbe090b25`.
The 135-file Java source snapshot matches the recording preparation snapshot. The local reference
commit remains C21 `ced673cefff3e18e54c6aca01e9ec346383d964f`; a newer published
implementation is not claimed at the November 10 verification cutoff.

## Feature timestamps

| Feature | Start | Video |
|---|---|---|
| F01 Course and material import | 00:14 | [Watch](https://www.youtube.com/watch?v=eNrlBO0PAuY&t=14s) |
| F02 Summaries | 01:36 | [Watch](https://www.youtube.com/watch?v=eNrlBO0PAuY&t=96s) |
| F03 Concept extraction | 01:58 | [Watch](https://www.youtube.com/watch?v=eNrlBO0PAuY&t=118s) |
| F04 Cited answers | 02:21 | [Watch](https://www.youtube.com/watch?v=eNrlBO0PAuY&t=141s) |
| F05 Flashcards and review | 03:21 | [Watch](https://www.youtube.com/watch?v=eNrlBO0PAuY&t=201s) |
| F06 Quiz generation | 05:48 | [Watch](https://www.youtube.com/watch?v=eNrlBO0PAuY&t=348s) |
| F07 Adaptive difficulty | 09:44 | [Watch](https://www.youtube.com/watch?v=eNrlBO0PAuY&t=584s) |
| F08 Attempts and grading | 07:17 | [Watch](https://www.youtube.com/watch?v=eNrlBO0PAuY&t=437s) |
| F09 Weak-topic ranking | 10:07 | [Watch](https://www.youtube.com/watch?v=eNrlBO0PAuY&t=607s) |
| F10 Study plans | 10:43 | [Watch](https://www.youtube.com/watch?v=eNrlBO0PAuY&t=643s) |
| F11 Progress history and restart | 14:18 | [Watch](https://www.youtube.com/watch?v=eNrlBO0PAuY&t=858s) |
| F12 Natural-language agent | 12:49 | [Watch](https://www.youtube.com/watch?v=eNrlBO0PAuY&t=769s) |

F07 also shows [insufficient history at 04:48](https://www.youtube.com/watch?v=eNrlBO0PAuY&t=288s); F09 shows
[insufficient graded evidence at 05:21](https://www.youtube.com/watch?v=eNrlBO0PAuY&t=321s). F08
[reopens the saved attempt at 09:27](https://www.youtube.com/watch?v=eNrlBO0PAuY&t=567s). Actual
[CLI course/progress reads start at 15:01](https://www.youtube.com/watch?v=eNrlBO0PAuY&t=901s); the
[CLI agent and saved-conversation trace start at 16:31](https://www.youtube.com/watch?v=eNrlBO0PAuY&t=991s).

## Demonstrated behavior

The walkthrough includes duplicate-import rejection, generated summaries and
concepts, a cited answer and unrelated question, card reveal with remembered/not-yet
review, an invalid quiz count and all three question types, saved grading,
graded-only difficulty and topic ranking, a rejected past exam date, a saved plan,
repeated completion, restart restoration and actual GUI/CLI agent execution.

The saved progress shows two reviews, five submitted attempts, thirty graded
answers, eighteen correct answers and one completed task. Additional submissions
provide sufficient topic samples. These are demonstration inputs, not student
academic performance or KUMA evaluation. All six answers in the shown attempt
were graded; the new recording does not demonstrate pending-grading retry or
self-assessment. Those paths have separate automated and historical checks.

The [walkthrough guide](gui-walkthrough.md) remains a rerun guide. The older
November 3 rendered CLI replay panels are superseded by this native recording.
The large master video is hosted separately from the source repository.

## Verification and remaining delivery

An isolated Java 17 clean build passed 361 tests in 46 classes, with no failures,
errors or skips. The source copy excluded credentials, runtime data and old build
output. The native demonstration is separate evidence from the offline suite.
The student completed focused guided review questions about the later core paths;
no exhaustive source-line audit is claimed.

Signed-out playback and all twelve feature plus two CLI timestamp seeks passed.
The uploaded video offers 1080p60 HD. These checks concern access and navigation,
not semantic grading quality or Stage 3 KUMA evaluation.
The report records scoped token totals and equivalent API costs. Actual payments
are not allocated; the final published-clone check remains open. The student submits the repository URL through the course system.
