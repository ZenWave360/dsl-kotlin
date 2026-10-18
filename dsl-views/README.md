# dsl-views

Mermaid diagrams from ZDL and ZFL models, as plain text. Common Kotlin code (JVM and JS), depends on `dsl-kotlin`.

- The output is inert Mermaid: no `click`, `link` or `callback` directives and no remote references.
- The output is deterministic: modules, events and edges are sorted, so the same inputs give the same text.
- Nothing is read from the filesystem or the network; every entry point takes file contents.

Maven artifact: `io.zenwave360.dsl:dsl-views` (`-jvm`, `-js`). It is not published to npm yet. The JS build
exports `generateMermaidFromZdl(zdl)` (`DslViewsJsExports.kt`).

## Diagrams

| Diagram | Entry point | Input |
| --- | --- | --- |
| ZDL class diagram | `GenerateMermaidFromZdl().execute(zdl)` | One ZDL text |
| ZFL flowchart and sequence diagrams | `GenerateMermaidFromZfl().execute(zfl)` | One ZFL text |
| Architecture diagram | `ZdlArchitectureReader().read(sources)`, then `ZdlArchitectureToMermaidTransformer().transform(architecture)` | Several ZDL texts |

The class diagram shows aggregates with their commands, entities with their fields, enums with their values,
inputs, outputs and events, services with their commands, and relationships with their cardinality.

The ZFL result has a flowchart and one sequence diagram per outcome (`MermaidDiagramsView`).

## Architecture model and diagram

Each ZDL file is one module: a microservice, or one module of a monolith. `ZdlArchitectureReader` parses the files
and returns an immutable `ZdlArchitecture`:

```kotlin
val architecture = ZdlArchitectureReader().read(
    listOf(
        ZdlSource("orders/orders-domain-model.zdl", ordersZdl),
        ZdlSource("payments/payments-domain-model.zdl", paymentsZdl),
    ),
)
architecture.modules; architecture.events; architecture.consumptions; architecture.calls
architecture.problems   // "file: message"; unresolvable references are reported, never thrown
```

- Modules: id, slug, display name, path, package. A module only named by a `zdl client` that is not in the set
  is a referenced module (`referenced = true`).
- Events: owned by the module that declares them; `EXTERNAL` when the `@asyncapi` annotation has a `topic`,
  else `INTERNAL`. Events a service consumes from an AsyncAPI channel that no module owns are foreign events.
- Consumptions: from `@listener(event: E)`, `@listener(zdl: X, event: E)` and `@asyncapi(channel: C)` on service
  methods, with the consuming method.
- Calls: from `@calls(zdl: X, command: Service.method)` on aggregate commands and service methods.

`ZdlArchitectureToMermaidTransformer(legacyShapes, maxModules, maxEvents)` draws the model as a `flowchart` with
modules as hexagons, events in their module box and external events as pipes. Defaults: `legacyShapes = false`
(`true` draws pipes as `[(Name)]` in a `flowchart TB`), `maxModules = 8`, `maxEvents = 15`. Above the thresholds
the result is an overview (modules only, one edge per publisher and consumer pair) plus one diagram per module.

```kotlin
val result = ZdlArchitectureToMermaidTransformer().transform(architecture)
result.diagrams.forEach { println("${it.title} (${it.moduleId})\n${it.mermaid}") }
println(result.legend)   // the same text as ZdlArchitectureToMermaidTransformer.LEGEND
```

## Tests

`src/commonTest/resources/arcadia-editions-modulith` holds a copy of the public Arcadia Editions modulith
ZDL files (see its README). Other fixtures are shared with dsl-kotlin (`dsl-kotlin/src/commonTest/resources`).
`samples/` is a local, git-ignored folder for previewing views.

```bash
./gradlew :dsl-views:jvmTest
./gradlew :dsl-views:allTests   # JVM, JS node and JS browser (needs a Chromium-based browser)
```
