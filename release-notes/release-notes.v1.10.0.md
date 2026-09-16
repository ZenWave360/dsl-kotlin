## What's Changed

Unreleased draft for **1.10.0**.

## What's New

### Mermaid class diagrams for ZDL

- Adds `io.zenwave360.language.zdl.application.GenerateMermaidFromZdl` (`execute(zdlContent: String): String`) in common code, and the JS export `generateMermaidFromZdl(input)`. It returns a Mermaid `classDiagram` with aggregates and their commands, entities with their fields, enums with their values, inputs, outputs, events, services with their commands, and relationships with cardinality. The output contains no click, link or callback directives and no remote references.

### Browser support for the JS package

- The JS target declares `browser()`. `check` runs `jsBrowserTest` (Karma, headless Chrome/Chromium/Edge) and fails if the library cannot load or work without a Node runtime.
- Node-only test dependencies (`fs` npm placeholder, `kotlin-node` wrappers) were removed; tests read fixtures via `process.getBuiltinModule`. Published artifacts are unaffected.

## Behaviour changes

### Negative numeric literals keep their sign (all platforms)

- `INT` and `NUMBER` tokens in `Zdl.g4` and `Zfl.g4` accept a leading `-`. `neg -2` in a config or plugin block now parses to `-2` (JVM `Long(-2)`), `min(-5)` to `"-5"`, and `-2.5` to `"-2.5"`. Previously the sign was dropped with a token-recognition error. Documents without negative literals lex exactly as before.

### JS: integers are plain numbers (JavaScript only)

- `parseZdl` and `parseZfl` return integers as JS `number`s (or as the exact decimal string beyond ±(2^53 - 1)) instead of Kotlin `Long` objects with compiler-mangled fields (`{"h2_1":3,"i2_1":0}`). This is visible to JS consumers that serialised or inspected those objects. `String(value)` still yields the same text. The JVM model is unchanged.
