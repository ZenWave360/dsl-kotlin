
ZenWave Domain and Flow Language
=====================================

[![Maven Central](https://img.shields.io/maven-central/v/io.zenwave360.dsl/dsl-kotlin.svg?label=Maven%20Central&logo=apachemaven)](https://search.maven.org/artifact/io.zenwave360.dsl/dsl-kotlin)
[![build](https://github.com/ZenWave360/zdl-kotlin/workflows/Verify%20Main%20and%20Publish%20Coverage/badge.svg)](https://github.com/ZenWave360/zdl-kotlin/actions/workflows/main.yml)
[![line coverage](https://raw.githubusercontent.com/ZenWave360/zdl-kotlin/badges/coverage.svg)](https://github.com/ZenWave360/zdl-kotlin/actions/workflows/main.yml)
[![branch coverage](https://raw.githubusercontent.com/ZenWave360/zdl-kotlin/badges/branch-coverage.svg)](https://github.com/ZenWave360/zdl-kotlin/actions/workflows/main.yml)
[![GitHub](https://img.shields.io/github/license/ZenWave360/zdl-kotlin)](https://github.com/ZenWave360/zdl-kotlin/blob/main/LICENSE)

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

NOTE: pending publishing to npm-registy

```bash
npm install @zenwave360/dsl
```

```js
import { parseZdl } from '@zenwave360/dsl';
import { parseZfl } from '@zenwave360/dsl';

const zdlContent = "...";
const zdlModel = parseZdl(zdlContent);

const zflContent = "...";
const zflModel = parseZfl(zflContent);
```

* Further reading:
- [ZDL Domain Language Reference](https://www.zenwave360.io/docs/event-driven-design/zenwave-domain-language/)
- [ZFL Flow Language Reference](https://www.zenwave360.io/docs/event-driven-design/zenwave-flow-language/)
- [ZenWave SDK](https://zenwave360.github.io/zenwave-sdk/)
- [ZenWave Editor for IntelliJ](https://zenwave360.github.io/plugin/)
