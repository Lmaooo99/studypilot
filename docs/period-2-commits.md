# Period 2 Commit Summary

Period: October 27-November 2, 2026. Times use America/Toronto.

| Batch | Work | Commit | Committed | Push checked | Verification |
|---|---|---|---|---|---|
| C08 | generate mixed quizzes with factory methods | [f307408](https://github.com/Lmaooo99/studypilot/commit/f307408a1f7f893082525d39c49a1cc630d69a0b) | 2026-10-27 06:42:26 | 2026-10-27 06:42:28 | 73 tests passed; real API mixed quiz; CLI restart and course-isolation checks |
| C09 | create quiz attempts and grade objective answers | [22156e6](https://github.com/Lmaooo99/studypilot/commit/22156e64286fb3323ea0d95b3dedf4f748b772c2) | 2026-10-27 08:09:25 | 2026-10-27 08:09:27 | 93 tests passed; packaged CLI attempts, graded/pending results, restart and duplicate-submission checks |
| C10 | grade short answers and resolve pending results | [a6aeaa2](https://github.com/Lmaooo99/studypilot/commit/a6aeaa2d231184064fe5fb112ac72ce30de3bec7) | 2026-10-28 14:24:58 | 2026-10-28 14:25:01 | 110 tests passed; real API grading, pending retry, self-assessment and restart checks |
| C11 | suggest quiz difficulty from graded performance | [9658d77](https://github.com/Lmaooo99/studypilot/commit/9658d770360feaaccce130ddac6963d86a707584) | 2026-10-28 15:44:52 | 2026-10-28 15:44:55 | 130 tests passed; threshold, isolation, pending-exclusion and override checks; read-only CLI restart checks |
| C12 | identify weak topics and explain study priorities | [Weak-topic feature history](https://github.com/Lmaooo99/studypilot/commits/main/src/main/java/studypilot/WeakTopicAnalyzer.java) | See Git history | See Git history | 147 tests passed; real explanation, missing-key fallback, minimum, scope and unchanged-study-record checks |

C13-C14 remain planned. Objective grading and self-assessment require no API calls.
The quiz and attempt GUI has compiled; its interactive walkthrough remains pending.
