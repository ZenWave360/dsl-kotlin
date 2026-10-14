## What's Changed

Unreleased draft for **1.10.0**.

## What's New

### New modules: dsl-views and dsl-docs

- `io.zenwave360.dsl:dsl-views` holds the Mermaid diagrams of ZDL and ZFL models. `dsl-kotlin` no longer contains diagram code.
- `io.zenwave360.dsl:dsl-docs` generates Markdown documentation for one service or monolith from its ZDL, ZFL, OpenAPI and AsyncAPI files, with the `dsl-views` diagrams as inline Mermaid blocks. It is a library (`DslDocs.generate`) and a command line tool (`dsl-docs build --config <file> --out <dir>`). Output is deterministic and nothing is loaded from the network.
- Neither module is published to npm yet.

### Mermaid class diagrams for ZDL

- Adds `io.zenwave360.language.zdl.application.GenerateMermaidFromZdl` (`execute(zdlContent: String): String`) in `dsl-views`. It returns a Mermaid `classDiagram` with aggregates and their commands, entities with their fields, enums with their values, inputs, outputs, events, services with their commands, and relationships with cardinality. The output contains no click, link or callback directives and no remote references.

### Browser support for the JS package

- The JS target declares `browser()`. `check` runs `jsBrowserTest` (Karma, headless Chrome/Chromium/Edge) and fails if the library cannot load or work without a Node runtime.
- Node-only test dependencies (`fs` npm placeholder, `kotlin-node` wrappers) were removed; tests read fixtures via `process.getBuiltinModule`. Published artifacts are unaffected.

### Repeated plugin declarations

- `parseZdl` reports every plugin declaration in `pluginsList`, including when several share a type name. Each instance's source span is `locations['pluginsList.N']`. The name-keyed `plugins` map is gone.

### Shared plugin CLI options

- ZDL accepts CLI options such as `--force` directly inside `config`, before `plugins`. The parser reports them in the top-level `cliOptions` map, with declaration and value spans matching plugin-local CLI options.

## Behaviour changes

### Mermaid diagrams moved from dsl-kotlin to dsl-views (breaking)

- `GenerateMermaidFromZfl`, `ZflToMermaidDiagramsTransformer`, `MermaidDiagramsView`, `MermaidSequenceDiagram` and `MermaidSequenceRenderMode` (available in `dsl-kotlin` since 1.8.0) are now in `dsl-views`, with the same package names. JVM and Kotlin consumers add the `io.zenwave360.dsl:dsl-views` dependency; imports do not change.
- The JS export `generateMermaidFromZdl`, which only appeared in 1.10.0 `next` prereleases of `@zenwave360/dsl`, is removed from that package. It is exported by `dsl-views`, which has no npm package yet.

### Structured ZDL syntax diagnostics

- `ZdlParser.parse` exposes syntax problems in `ZdlParseResult.syntaxProblems` and suppresses
  ANTLR's direct console output.
- `parseModel` returns only the first syntax problem and skips semantic validation for malformed
  input, preventing recovery artifacts from cascading into misleading semantic errors.
- Syntax problem locations use the same six-integer, zero-based-column representation as other
  model locations so LSP and CLI consumers can normalize them consistently.

### Negative numeric literals keep their sign (all platforms)

- `INT` and `NUMBER` tokens in `Zdl.g4` and `Zfl.g4` accept a leading `-`. `neg -2` in a config or plugin block now parses to `-2` (JVM `Long(-2)`), `min(-5)` to `"-5"`, and `-2.5` to `"-2.5"`. Previously the sign was dropped with a token-recognition error. Documents without negative literals lex exactly as before.

### JS: integers are plain numbers (JavaScript only)

- `parseZdl` and `parseZfl` return integers as JS `number`s (or as the exact decimal string beyond ±(2^53 - 1)) instead of Kotlin `Long` objects with compiler-mangled fields (`{"h2_1":3,"i2_1":0}`). This is visible to JS consumers that serialised or inspected those objects. `String(value)` still yields the same text. The JVM model is unchanged.
