# Native GUI walkthrough guide

This script prepares a future screen recording. The full native GUI walkthrough
has not passed: the automated check displayed the window and the empty-course
error, but could not reliably enter text. First confirm that a person can type in
the New course field. Record the application window and actual results once input
works. Keep the local configuration file out of the recording.

Use the tested Java 17 JAR and a separate demonstration data directory. Configure
the existing compatible API connection locally. Import samples/design-notes.md
from the repository. Generated wording and IDs can vary on another real run.

## A: materials and study content

1. In Courses and materials, enter a new course name and click Create course.
   Use Import material to select the sample. Show the course/material selections
   and the imported source locations. Try importing it again to show rejection.
2. Select brief and click Summarize. Show the busy/ready state and actual summary.
   Click Extract concepts and show the saved names and relationships.
3. In Questions, enter a question about Observer and use Ask with citations.
   Show the answer and its file/line references. Try an unrelated question and
   show the no-source response.
4. In Flashcards, request three cards and click Generate flashcards. Select a
   card, use Flip card and then Remembered or Not yet. Show the changed mastery
   and progress. Close and reopen the application with the same demo directory,
   select the same course, and show the saved content and review state.

## B: quizzes and feedback

1. In Quizzes, enter Design patterns, six questions and all three question types.
   Use Show suggestion, select a difficulty and click Generate quiz. Show the
   actual question count and the saved quiz. Use Start attempt.
2. Enter multiple-choice option numbers, true/false values and short answers in
   Your answer. Use Submit answers and show the score and GRADED/PENDING statuses.
   Use demonstration answers and label their purpose when recording.
3. If a real request fails and leaves a short answer pending, use Retry pending
   grading. For a remaining pending short answer, select its row and use
   Self-grade selected answer to show the saved answer/rubric and the explicit
   Correct/Incorrect/Cancel choices. Do not present a scripted model failure as a
   real service response.
4. Reopen a saved submitted attempt. Show that its answers remain fixed. Use
   sufficient explicitly labelled demonstration attempts to obtain at least five
   graded results for the topic. Show suggestion now reports the actual count and
   accuracy. In Progress, use Weak topics and show both the measured ranking and
   the model explanation or its honest failure notice.
5. Restart and select the same course. Show the restored counts and history;
   pending answers and flashcard reviews must remain separate from graded accuracy.

## C: plans and the assistant

1. In Study plans, enter a real upcoming exam date and whole daily hours. Use
   New plan, show dated tasks and daily minutes, select a task, check Completed
   selected task and use Save completion. Repeating the action must retain one
   completed task. Restart and use View plan to show the same saved task state.
2. In Assistant, leave Session ID blank for a new conversation. Request progress,
   the weakest measured topic and a three-question quiz using that topic. Show
   the actual plan, tool outputs and trace. If the model produces a result
   reference, show its resolution; otherwise describe only the plan that ran.
3. Save the displayed session ID, restart, select the same course and reuse that
   session for a follow-up progress request. Show the loaded context and output.
   Any actual clarification or failure should remain visible in the recording.

After recording, map every F01-F12 feature to its real segment start, identify
which interface was used, and check that inputs, outputs and model involvement
are readable. The student reviews and uploads the final videos; accessible
timestamp links must be added to the demonstration index and report.
