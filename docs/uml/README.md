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
| use-cases.uxf | Original UC01-UC15 with implemented constraints |
| SD01-SD14.uxf | Current import, study, grading, feedback, recovery and agent flows |

The class views cover the types in all 88 production Java source files and the
private SwingGUI.StudyWorker. They show selected fields and operations; parameter
names are included, while complete types and remaining members are in the source.
MockLLMClient is explicitly marked test only and is not part of the application JAR.
The five pattern roles are annotated in the detailed views.

Matching PNG exports are in images. Open a UXF file in UMLet to edit it. The images
were exported with the official UMLet 15.1 command-line converter:

```sh
java -Djava.awt.headless=true -jar /path/to/umlet.jar \
  -action=convert -format=png '-filename=docs/uml/*.uxf' -output=docs/uml/images
```

UMLet is an optional documentation tool and is not a production dependency.
Its [official FAQ](https://www.umlet.com/faq.htm) describes batch exports.
