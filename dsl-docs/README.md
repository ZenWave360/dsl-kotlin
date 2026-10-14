# dsl-docs

Markdown documentation for one service or monolith, generated from its ZDL, ZFL, OpenAPI and AsyncAPI files.

- Diagrams are inline Mermaid blocks from dsl-views. GitHub, GitLab and most Markdown viewers render them.
- The output is deterministic: the same inputs give byte-identical files on every platform. There is no
  timestamp unless you pass a date, sources are normalized to `\n` line endings before they are read, and the
  pages always use `\n`.
- Nothing is read from the network. `$ref`s to other files or URLs are shown as references, never loaded.

## Command line

```bash
# JBang reads the main class from the jar manifest and the dependencies from its POM:
jbang io.zenwave360.dsl:dsl-docs-jvm:<version> build --config dsl-docs.yml --out docs/zenwave
# from this repository:
./gradlew :dsl-docs:dslDocs -Pargs="build --config path/to/dsl-docs.yml --out docs/zenwave"
```

Exit codes: `0` done (problems found in the inputs are printed as `WARNING` lines), `1` the config or a file it
names cannot be read, or the output folder holds a file dsl-docs did not generate, `2` wrong arguments.

### Config

Paths are relative to the config file. Every key is optional; a page is written only when it has sources.

```yaml
title: Orders Checkout        # default: the ZDL config title, then the OpenAPI or AsyncAPI info title
version: 0.1.0                # default: the ZDL config version, then the OpenAPI or AsyncAPI info version
date: 2026-10-13              # printed as written; omitted when not set
zdl: [domain-model.zdl]
zfl: [../business-flows/place-order-flow.zfl]
openapi: [openapi.yml]
asyncapi: [asyncapi.yml, asyncapi-client.yml]
pages:                        # pages written by hand, linked from the index
  - { title: Service summary, path: SUMMARY.md }
```

JSON works too: it is valid YAML.

### Output

| File | Content | Written when |
| --- | --- | --- |
| `README.md` | Title, version, date, links to the pages and to `pages`, the sources with their SHA-256, problems | Always |
| `domain.md` | Per ZDL file: class diagram, glossary, commands, events | There are ZDL files |
| `flows.md` | Per ZFL file: flowchart and one sequence diagram per outcome | There are ZFL files |
| `apis.md` | Per OpenAPI file: operations and schemas | There are OpenAPI files |
| `events.md` | Per AsyncAPI file (v2 or v3): operations with channel and address, messages and payload schemas | There are AsyncAPI files |

Every page starts with a marker comment and one `<!-- Source: <path> sha256:<hash> -->` line per source, so a
change in the sources is visible in the diff of the generated pages.

The output folder belongs to dsl-docs. Keep hand-written pages elsewhere and link to them with `pages`.
The tool deletes generated pages that a run no longer produces, and stops without writing anything when a
file it would overwrite does not start with the generated-file marker.

## Library

The generator is common Kotlin code (JVM and JS). It takes file contents, not paths, and returns the files to
write, so a language server or another tool can call it without touching the filesystem:

```kotlin
val output = DslDocs().generate(
    DocsInput(
        sources = listOf(DocsSource(SourceKind.ZDL, "domain-model.zdl", zdlText)),
        title = "Orders Checkout",
    ),
)
output.files.forEach { file -> /* file.path, file.content */ }
output.problems.forEach { problem -> /* "domain-model.zdl: line 3:7: ..." */ }
```

`DocsConfig.parse(text)` reads a config file for callers that want the same config format.

## Diagrams

dsl-docs does not write diagram text. The Mermaid diagrams come from dsl-views
(`ZdlToMermaidClassDiagramTransformer` and `GenerateMermaidFromZfl`); dsl-docs places them in the pages. A new
diagram is added to dsl-views first.

## Tests

`src/commonTest/resources/arcadia-editions` holds copies of the public Arcadia Editions sample files (see its
README). `ArcadiaSnapshotTest` compares the generated pages with `src/commonTest/resources/snapshots/orders-checkout`.
After an intended change, regenerate the snapshots and review the diff:

```bash
DSL_DOCS_UPDATE_SNAPSHOTS=true ./gradlew :dsl-docs:jvmTest
```
