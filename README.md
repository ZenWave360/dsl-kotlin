

> **Work in Progress**: This repository is a migration attempt to Kotlin Multiplatform.

ZenWave Domain Model Language
=====================================

[![Maven Central](https://img.shields.io/maven-central/v/io.zenwave360.sdk/zdl-kotlin.svg?label=Maven%20Central&logo=apachemaven)](https://search.maven.org/artifact/io.zenwave360.sdk/zdl-kotlin)
[![build](https://github.com/ZenWave360/zdl-kotlin/workflows/Verify%20Main%20and%20Publish%20Coverage/badge.svg)](https://github.com/ZenWave360/zdl-kotlin/actions/workflows/main.yml)
[![coverage](https://raw.githubusercontent.com/ZenWave360/zdl-kotlin/badges/coverage.svg)](https://github.com/ZenWave360/zdl-kotlin/actions/workflows/main.yml)
[![GitHub](https://img.shields.io/github/license/ZenWave360/zdl-kotlin)](https://github.com/ZenWave360/zdl-kotlin/blob/main/LICENSE)

> Since version 1.3.0 groupId was changed to `io.zenwave360.sdk`

ZDL is a Domain Specific Language (DSL) for Event-Driven Architectures. With Domain Driven Design principles built-in, it can be used to map the discoveries of an [EventStorming](https://www.eventstorming.com/) session.

- Designed to be developer friendly, with a simple and compact syntax.
- It retains the language of the business process discoveries found in Event-Storming sessions.
- And because it's machine-friendly it can be parsed and converted into multiple software artifacts like: 
  - documentation, drawings, API definitions like OpenAPI and AsyncAPI v2/v3 and multiple backend implementations and its tests.

Further reading:
- [ZDL Domain Language Reference](https://zenwave360.github.io/docs/event-driven-design/zenwave-domain-language)
- [ZenWave SDK](https://zenwave360.github.io/zenwave-sdk/)
- [ZenWave Editor for IntelliJ](https://zenwave360.github.io/plugin/)

![EventStorming ZDL](docs/EvenStorming-ZDL.png)

