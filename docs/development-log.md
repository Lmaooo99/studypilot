# Development Log

## My project decisions

- I chose an AI study assistant because I wanted help studying my own course materials.
- I reduced the scope to 12 features to keep the project manageable.
- I reviewed the design drafts and asked for consistency between the features, use cases and UML diagrams.
- I clarified that weak-topic statistics should use graded quiz results, while flashcard reviews are recorded separately.
- I selected the compatible API and model and provided the connection settings.

## My review, integration and validation

- I read through the implementation and worked through the responsibilities of the main classes and methods.
- I reviewed the code against the Stage 1 design, including the feature flow and required patterns.
- I integrated the generated parts and checked the connections between components.
- I validated the implementation and reviewed the results.

## C01 Setup

Added the Maven build, command-line startup, Swing window and local configuration.
Feature screens are marked as planned until their implementation is added.

## C02 Course materials

Added Course, Material and Chunk classes, TXT/Markdown import and JSON storage.
Imports keep line references and reject empty, unsupported, oversized or duplicate
files. Unicode characters are preserved at chunk boundaries.

## C03 PDF materials

Added PDF imports using PDFBox. Text is read page by page, and saved chunks keep
the file name and page number. Corrupt PDFs, PDFs without readable text and files
above 20 MiB are rejected before any material is added.

## C04 Material summaries

Added LLMClient, OpenAIAdapter and OpenAIApiClient for Chat Completions requests.
Summaries support brief, detailed and bullet styles. Long materials use partial
summaries followed by a combined summary. Failed calls are retried once, and a
failed generation keeps the previous saved summary. GUI and CLI use AgentFacade.

## C05 Course concepts

Added concept extraction, JSON validation and saved source chunk IDs. Duplicate
names are merged, and invalid or cyclic parent links are removed. Existing
concepts are kept when extraction fails or returns an empty list. Course files
from before this feature can still be loaded.

## Checks

All 35 JUnit tests passed with Java 17 using `./mvnw -q clean verify`.
HTTP protocol tests use a local test server. A separate real API check generated
and saved a summary with the configured gpt-6.1-sol model. The concept check
extracted eight concepts and confirmed their source IDs, parent links and saved
data after a new process loaded the course. CLI argument checks and loading an
older course file also passed. The GUI compiles; its final walkthrough is still
pending.

## Next steps

Add retrieval and questions with validated citations.

## AI support

Codex was used to generate and revise code, prepare tests and troubleshoot errors.
