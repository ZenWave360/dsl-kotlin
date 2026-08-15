## What's Changed

Release **1.9.0** adds source-preserving ZDL configuration editing and a full-fidelity ZFL semantic model designed for architecture-graph consumers.

## What's New

### Full-Fidelity ZFL Semantics

- Preserves every execution occurrence of a logical ZFL operation instead of collapsing repeated `do`/`when` declarations into a single lossy command view.
- Captures occurrence-local documentation, triggers, annotations, compensation targets, actors, timers, service references, action steps, emissions, responses, stable occurrence keys, and declaration order.
- Adds semantic representations for response details, failure emissions, flow options, start fields, end descriptions, and compensation policies.
- Propagates source references across flows, operations, starts, action steps, handlers, emissions, responses, and outcomes. `ZflParser.parseModel` now accepts an optional source name for accurate diagnostics and graph provenance.
- Retains the legacy merged `ZflCommand` fields as a compatibility view while exposing the lossless occurrence model through `ZflCommand.occurrences`.
- Resolves ZFL systems together with their declared services, giving downstream graph builders enough context to map operations to manifest services and ZDL methods.

### Source-Preserving ZDL Editing

- Adds the JVM `ZdlEditor` API with `setConfigString(Path, name, value)` for updating an existing string-valued top-level `config` option.
- Records source ranges for ZDL configuration values and rewrites only the selected scalar, preserving surrounding formatting and content.
- Escapes replacement strings according to ZDL syntax and reports targeted errors for missing, non-string, or invalid configuration fields.

### Build and Release Tooling

- Aligns the Kotlin Multiplatform toolchain on Kotlin 2.3.0, Gradle 8.14, Java 17, and `antlr-kotlin-runtime` 1.0.4.
- Adds a credential-separated release workflow for Maven Central and optional npm publication, with release notes used directly as the GitHub release body.

**Full Changelog**: https://github.com/ZenWave360/dsl-kotlin/compare/v1.8.0...v1.9.0
