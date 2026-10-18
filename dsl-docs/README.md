# dsl-docs

Markdown documentation for one service or monolith, generated from its ZDL, ZFL, OpenAPI and AsyncAPI files.

- Diagrams are inline Mermaid blocks from dsl-views. GitHub, GitLab and most Markdown viewers render them.
- The output is deterministic: the same inputs give byte-identical files on every platform. There is no
  timestamp unless you pass a date, sources are normalized to `\n` line endings before they are read, and the
  pages always use `\n`.
- Nothing is read from the network: `http(s)` `$ref`s are never loaded and show as external classes. Relative `$ref`s to files
  next to the spec (Avro `.avsc`, shared schemas) are read through the file reader of `DocsFiles` (JVM); the files that were read are listed,
  with their hashes, in the header of the page, so `check` notices when they change. In JS (no file reader) they stay external.

## Usage

dsl-docs is a library. From the command line use the zenwave-sdk plugin, which reads the files, calls this library
and writes or checks the folder:

```bash
zw -p DslDocs ...
```

From JVM code, `DocsFiles` reads the files and `DocsFolder` writes (or checks) the output folder:

```kotlin
val baseDir = File(".")            // source paths in the pages are relative to it
val outDir = File("docs/zenwave")

val input = DocsFiles.input(
    baseDir = baseDir,
    outDir = outDir,
    zdlFiles = listOf("domain-model.zdl"),
    zflFiles = listOf("../business-flows/place-order-flow.zfl"),
    apiFiles = listOf("openapi.yml", "asyncapi.yml", "asyncapi-client.yml"),   // OpenAPI or AsyncAPI, told by content
    title = "Orders Checkout",                                                  // optional, like version and date
    links = listOf(DocsLink("Service summary", "../SUMMARY.md")),               // hand-written pages, relative to outDir
)
val output = DslDocs().generate(input)
output.problems.forEach { println("WARNING $it") }

val folder = DocsFolder(outDir)
val differences = folder.check(output)   // empty = up to date; nothing is written
val result = folder.write(output)        // result.written, result.deleted
```

Paths are relative to `baseDir` or absolute. API files are told apart by `SourceKind.ofApi(content)`: an `openapi` or
`swagger` top level key is OpenAPI, `asyncapi` is AsyncAPI. A missing file, or an API file that is neither, is an
`IllegalArgumentException`. Relative `$ref` files (Avro `.avsc`, shared schemas) are read next to the sources; their
hashes are listed in the page header, so `check` notices when they change.

### Ownership and `check`

dsl-docs owns files by marker, not by folder. A `.md` file, at any depth (`modules/` too), whose first line is the
generated-file marker is dsl-docs's: it is overwritten, and deleted when a run no longer produces it (together with
the subfolders that this leaves empty). Every other file in the output folder is hand-written and never touched. So
`architecture.md` can live in `docs/` next to `reading-zdl.md`, `development.md` or `adr/`. The only error is a page
dsl-docs would generate at the path of a hand-written file: `check` and `write` throw `IllegalArgumentException`
and write nothing.

`DocsFolder.check` compares the generated pages with the folder (line endings are ignored) and returns every
`DocsDifference(path, reason)` with reason `missing`, `changed` or `stale, no longer generated`. Use it in CI so the
committed docs cannot go stale.

### Output

| File | Content | Written when |
| --- | --- | --- |
| `README.md` | Title, version, date, links to the pages and to `pages`, the sources with their SHA-256, problems | Always |
| `architecture.md` | Diagram of the modules (one per ZDL file) with their events and direct calls, event table, module table, direct calls | There are ZDL files |
| `domain.md` | Per ZDL file: class diagram, glossary, commands, events | There is one ZDL file |
| `modules/<module>.md` | The same for one module, plus the events it consumes and the calls it makes | There are several ZDL files (replaces `domain.md`) |
| `flows.md`, `flows/<flow>.md` | Index of the flows, and one page per flow: happy path, flowchart, outcomes, systems (linked to module pages) and every other path as a sequence diagram | There are ZFL files |
| `apis.md` | Per OpenAPI file: operations table, then schemas grouped by response with a class diagram and field tables | There are OpenAPI files |
| `events.md` | Per AsyncAPI file (v2 or v3): channels and operations defined in the file (Provided, with a class diagram and field tables per message), then the channels defined in other files through `$ref` (Client of) | There are AsyncAPI files |

Every page starts with a marker comment and one `<!-- Source: <path> sha256:<hash> -->` line per source, so a
change in the sources is visible in the diff of the generated pages.

Hand-written pages may sit in the same folder (see Ownership above); link them from the index with `links`.

## Without the filesystem

The generator itself is common Kotlin code (JVM and JS). It takes file contents, not paths, and returns the files to
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

To regenerate documentation of real projects while developing this repository (not shipped), run the test-scope
`DevDocs` through Gradle:

```bash
./gradlew -q :dsl-docs:devDocs -Pargs="--zdl a.zdl,b.zdl --zfl x.zfl --api openapi.yml,asyncapi.yml --out build/docs/x [--title T] [--base dir] [--check]"
```
