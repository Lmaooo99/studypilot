# Implemented UML

These UMLet 15.1 sources describe the Stage 2 implementation. The original
Stage1-Report.pdf remains the design baseline.

| Source | Content |
|---|---|
| class-view-0.uxf | Application overview |
| class-view-1.uxf | GUI/CLI, Facade, agent planning/execution, tools and memory |
| class-view-2.uxf | Configuration, LLMClient, Adapter, prompts and parsers |
| class-view-3.uxf | Courses, materials, concepts, cards, answers and retrieval Strategy |
| class-view-4.uxf | Typed questions, Factory Method, attempts and grading Strategy |
| class-view-5.uxf | Progress recovery, saved events and Observer |
| class-view-6.uxf | Validated study plans and task completion |
| class-view-7.uxf | JSON repositories and file helper |
| class-view-8.uxf | Recovery fields, pending cache writes and saved task completion time |
| use-cases.uxf | Original UC01-UC15 with implemented constraints |
| SD01-SD14.uxf | Current import, study, grading, feedback, recovery and agent flows |

The nine class views cover the types in all 88 production Java source files and the
private SwingGUI.StudyWorker. They show selected fields and operations; parameter
names are included, while complete types and remaining members are in the source.
MockLLMClient is explicitly marked test only and is not part of the application JAR.
The five pattern roles are annotated in the detailed views.
Most sequence diagrams focus on the requested service operation. AgentFacade also
loads the selected course and runs progress recovery before most course-scoped
operations; this shared prelude is shown in SD12 rather than repeated in every
diagram. That prelude can repair derived cache files.
The Word/PDF report includes views 1 through 7 in a large-format appendix. Tall
views continue on later sheets at gaps between class rows; connecting lines
continue across those sheets. Continuation notes identify the Observer realization
lines and the ProgressRestorer dependency on HistoryRecorder in class view 5.
The full-size exports retain each complete view. These large
digital pages are intended for 100% viewing; shrinking them to Letter paper reduces
the member text substantially.

The November 9 revision includes the facade snapshot methods, actual quiz-topic
lookup, normalized tool arguments and atomic conversation turns.
The recheck also adds the graded-sample comparison, pending-write retry operation,
the missing facade/progress/repository methods and the explicit course read in
SD10/SD12. Service returns pass through AgentFacade.
SD05/SD11/SD12
distinguish primary source saves from recoverable progress/history writes;
SD13/SD14 show one turn save and nested step/retry/replan behavior. SD04 includes
the labelled uncited-answer path. There are 24 editable sources and 24 PNG exports.

Matching PNG exports are in images. Open a UXF file in UMLet to edit it. The images
were exported with the official UMLet 15.1 command-line converter:

```sh
java -Djava.awt.headless=true -jar /path/to/umlet.jar \
  -action=convert -format=png '-filename=docs/uml/*.uxf' -output=docs/uml/images
```

UMLet is an optional documentation tool and is not a production dependency.
Its [official FAQ](https://www.umlet.com/faq.htm) describes batch exports.
