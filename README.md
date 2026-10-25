# StudyPilot

StudyPilot is an AI study and learning assistant for EECS3311. The design baseline
is [Stage1-Report.pdf](Stage1-Report.pdf).

## Current functionality

Create courses and import TXT, Markdown or PDF materials through the GUI or CLI.
Imports keep the file name and original line ranges or PDF page numbers. Generate
material summaries and extract concepts through the configured OpenAI-compatible
service. Questions, flashcards, quizzes, study plans and the agent will be added
later.

## Build and run

Use JDK 17 or newer. The first Maven Wrapper build needs Internet access.

```sh
./mvnw clean verify
java -jar target/studypilot.jar help
java -jar target/studypilot.jar status
java -jar target/studypilot.jar gui
```

On Windows, use mvnw.cmd. Eclipse can import the repository as a Maven project.

## Course materials

```sh
java -jar target/studypilot.jar course add "Software Design"
java -jar target/studypilot.jar course list
java -jar target/studypilot.jar material import samples/design-notes.md --course COURSE_ID
java -jar target/studypilot.jar material list --course COURSE_ID
```

Replace COURSE_ID with the ID printed when creating a course. Imports accept
.txt, .md and .pdf files up to 20 MiB. Empty, unreadable, unsupported and duplicate files
are rejected. Long text is split into overlapping chunks while preserving complete
Unicode characters and line ranges.

PDF text is extracted one page at a time, so each chunk keeps its original page
number. PDFs without readable text, including scanned pages without a text layer,
are rejected. OCR is not included.

## Material summaries

Copy .env.example to .env and set LLM_BASE_URL, LLM_API_KEY and LLM_MODEL for your
service. Include the API version path, such as /v1, in the base URL. The default
model is gpt-6.1-sol. Set LLM_REASONING_EFFORT to an empty value if your service
does not accept that optional parameter.

```sh
java -jar target/studypilot.jar summarize MATERIAL_ID --course COURSE_ID --style brief
```

Replace MATERIAL_ID with an imported material's ID. Styles are brief, detailed and
bullet. In the GUI, select a course and material, choose a style and click
Summarize. The result is saved with the material.

Long materials are summarized in batches of 20 chunks and then combined. A summary
accepts at most 400 chunks. A failed call is retried once. If generation still
fails, the previous saved summary is kept and an error is displayed.

## Course concepts

```sh
java -jar target/studypilot.jar concepts extract MATERIAL_ID --course COURSE_ID
java -jar target/studypilot.jar concepts list --course COURSE_ID
```

In the GUI, select a course and material and click Extract concepts. Each concept
has a name, definition, optional parent and source chunk IDs. Names are merged
without regard to letter case. Sources must belong to the supplied material
chunks. Invalid parent links and cycles are removed before saving.

Extraction accepts 1 to 400 chunks, processed in batches of 20. Invalid responses
are retried once. If extraction fails or finds no concepts, earlier saved concepts
are kept. Saved concepts can be listed after restarting the application.

## Configuration and storage

Runtime files are saved under data/courses by default. A new process reloads the
saved course, material and chunk IDs. DATA_DIR can be set in a local .env file.
Keep real settings local. Course creation and imports do not require an API key.
API token usage is recorded in llm-usage.jsonl in the data directory, without
prompts or credentials.

## Development records

See [the development log](docs/development-log.md) for decisions and checks, and
[the commit summary](docs/period-1-commits.md) for publication progress.
