
ZenWave Domain and Flow Language
=====================================

[![Maven Central](https://img.shields.io/maven-central/v/io.zenwave360.dsl/dsl-kotlin.svg?label=Maven%20Central&logo=apachemaven)](https://search.maven.org/artifact/io.zenwave360.dsl/dsl-kotlin)
[![build](https://github.com/ZenWave360/dsl-kotlin/actions/workflows/publish-maven-snapshots.yml/badge.svg?branch=develop)](https://github.com/ZenWave360/dsl-kotlin/actions/workflows/publish-maven-snapshots.yml)
[![line coverage](https://raw.githubusercontent.com/ZenWave360/dsl-kotlin/badges/coverage.svg)](https://github.com/ZenWave360/dsl-kotlin/actions/workflows/main.yml)
[![branch coverage](https://raw.githubusercontent.com/ZenWave360/dsl-kotlin/badges/branches.svg)](https://github.com/ZenWave360/dsl-kotlin/actions/workflows/main.yml)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](https://github.com/ZenWave360/dsl-kotlin/blob/main/LICENSE)

`dsl-kotlin` provides Kotlin Multiplatform parsers for both ZDL and ZFL.

- [ZDL](https://www.zenwave360.io/docs/event-driven-design/zenwave-domain-language/) is a Domain Specific Language (DSL) for Event-Driven Architectures. With Domain Driven Design principles built-in, it can be used to map the discoveries of an [EventStorming](https://www.eventstorming.com/) session.
- [ZFL](https://www.zenwave360.io/docs/event-driven-design/zenwave-flow-language/) is a flow language for modelling event-driven workflows, commands, outcomes, policies, and terminal flow states.

Both languages are designed to be developer-friendly, compact, and machine-readable so they can be parsed and converted into software artifacts such as:
- documentation
- diagrams
- API definitions like OpenAPI and AsyncAPI v2/v3
- backend implementations and tests

![EventStorming ZDL](docs/EvenStorming-ZDL.png)
![ZFL Flow](https://ivangsa.com/assets/articles/arcadia-editions/zfl-flow.png)

Usage:

* Java:

```xml
<dependency>
    <groupId>io.zenwave360.dsl</groupId>
    <artifactId>dsl-kotlin-jvm</artifactId>
    <version>${dsl-kotlin.version}</version>
</dependency>
```

```java
String zdlContent = "...";
ZdlParser parser = new ZdlParser();
ZdlModel model = parser.parseModel(zdlContent);

String zflContent = "...";
ZflParser flowParser = new ZflParser();
ZflModel flowModel = flowParser.parseModel(zflContent);
```

NOTE: JVM version includes working `ZdlParser` and `ZflParser` implementations compiled from the Kotlin ANTLR4 target and also the Java target parser/lexer classes used by `intellij-antlr-adapter`.

* JavaScript/TypeScript:

For package build and publishing instructions, see [Publishing to npm](docs/npm-publishing.md).

```bash
npm install @zenwave360/dsl@next
```

The `next` tag selects the opt-in prerelease train. Stable releases use `latest`.

```js
import { parseZdl } from '@zenwave360/dsl';
import { parseZfl } from '@zenwave360/dsl';

const zdlContent = "...";
const zdlModel = parseZdl(zdlContent);

const zflContent = "...";
const zflModel = parseZfl(zflContent);
```

ZDL plugin option source spans are available in `model.locations` under
`pluginsList.<n>.config.<option>` and `pluginsList.<n>.cliOptions.<option>`.
Each declaration has a span; its `.value` span covers only the value, including
quote delimiters. A CLI flag without a value has no `.value` span. The first two
span entries are the start offset and exclusive end offset in the original
source, suitable for `source.slice(span[0], span[1])`. Model values are unchanged.

`model.pluginsList` is every plugin declaration in order. Each instance's
declaration span is `locations['pluginsList.N']`.

CLI options may also be declared directly inside `config`, before `plugins`,
for consumers that apply shared command-line defaults. They are reported in
`model.cliOptions`; their spans use `cliOptions.<option>` and
`cliOptions.<option>.value`, with no value span for a valueless flag.

These spans are implemented in shared Kotlin source. JS consumers must rebuild
the package with `./gradlew jsProductionExecutableCompileSync` and update their
bundled or installed artifact; an existing JS bundle does not gain them automatically.

Numbers in `parseZdl` and `parseZfl` output are plain JavaScript values: an integer is a JS
`number` (for example `maxDepth 3` gives `3`), or its exact decimal string when it lies beyond
`Number.MAX_SAFE_INTEGER` (±(2^53 - 1)). Decimals stay strings (`ratio 1.25` gives `"1.25"`),
as in the JVM model. Negative literals keep their sign (`neg -2` gives `-2`).
Before 1.10.0 an integer was an opaque Kotlin `Long` object with compiler-generated field names
(`{"h2_1":3,"i2_1":0}`) and the sign of a negative literal was dropped; JS consumers that worked
around either should read the value directly. The JVM model is unchanged (`Long` and `String`).

The repository builds three modules:

| Module | Maven artifact | Content |
| --- | --- | --- |
| `dsl-kotlin` | `io.zenwave360.dsl:dsl-kotlin` | ZDL and ZFL parsers, semantic models, event flow view models; npm `@zenwave360/dsl` |
| `dsl-views` | `io.zenwave360.dsl:dsl-views` | Mermaid diagrams from ZDL and ZFL models: class diagram, flows, and the architecture of several ZDL modules; see [dsl-views/README.md](dsl-views/README.md) |
| `dsl-docs` | `io.zenwave360.dsl:dsl-docs` | Markdown documentation with those diagrams, from ZDL, ZFL, OpenAPI and AsyncAPI files; see [dsl-docs/README.md](dsl-docs/README.md) |

ZDL class diagrams and ZFL flowcharts and sequence diagrams are Mermaid text from `dsl-views`:

```kotlin
val classDiagram = GenerateMermaidFromZdl().execute(zdlContent)
val flowDiagrams = GenerateMermaidFromZfl().execute(zflContent)
```

The class diagram contains aggregates with their commands, entities with their fields, enums with their
values, inputs, outputs and events, services with their commands, and relationships with their
cardinality. It contains no `click`, `link` or `callback` directives and no remote references.
`dsl-views` and `dsl-docs` are not published to npm yet.

The JS package loads in browsers and Web Workers as well as in Node.js: `./gradlew check`
runs `jsBrowserTest` in a headless Chromium-based browser (Karma; set `CHROME_BIN`, or a local
Chrome, Chromium or Edge is used).

* Further reading:
- [ZDL Domain Language Reference](https://www.zenwave360.io/docs/event-driven-design/zenwave-domain-language/)
- [ZFL Flow Language Reference](https://www.zenwave360.io/docs/event-driven-design/zenwave-flow-language/)
- [ZenWave SDK](https://zenwave360.github.io/zenwave-sdk/)
- [ZenWave Editor for IntelliJ](https://zenwave360.github.io/plugin/)
