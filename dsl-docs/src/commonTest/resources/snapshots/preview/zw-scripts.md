# Arcadia Editions - Orders Checkout scripts

Generation scripts of the Arcadia Editions orders-checkout service.

## Contents

- [Configuration](#configuration)
- [Plugins](#plugins)
  - [ZDLToOpenAPIPlugin](#zdltoopenapiplugin)
  - [ZDLToAsyncAPIPlugin](#zdltoasyncapiplugin)
  - [ZdlToMarkdownPlugin (disabled)](#zdltomarkdownplugin-disabled)
  - [TeamOwnPlugin](#teamownplugin)

Plugin descriptions come from the plugin catalogue: ZenWave SDK 2.8.0-SNAPSHOT.

## Configuration

The options of the `config` block. Every plugin inherits them, and the command-line options, unless it declares `(inherit false)`; an option the plugin sets itself replaces the inherited one.

| Property | Value |
| --- | --- |
| id | urn:com.arcadiaeditions:orders:checkout:scripts |
| title | Arcadia Editions - Orders Checkout scripts |
| basePackage | io.example.orders |
| targetFolder | . |

Command-line options:

| Option | Value |
| --- | --- |
| --force |  |
| --profile | local build |

## Plugins

Each plugin in the order the file declares them, with what it is for, its own options and the options it ends up with.

### ZDLToOpenAPIPlugin

Generates a draft OpenAPI definitions from your ZDL entities and services.

#### Own configuration

| Property | Value | Description |
| --- | --- | --- |
| idType | integer | JsonSchema type for id fields and parameters. (String, default `string`) |
| targetFolder | build/generated | Target folder to generate code to. If left empty, it will print to stdout. (File, default `null`) |
| targetFile | ./openapi.yml | Target file (String, default `openapi.yml`) |

| Option | Value |
| --- | --- |
| --profile | test |

#### Resolved configuration

| Property | Value | From | Description |
| --- | --- | --- | --- |
| id | urn:com.arcadiaeditions:orders:checkout:scripts | config |  |
| title | Arcadia Editions - Orders Checkout scripts | config | API Title (String, default `null`) |
| basePackage | io.example.orders | config |  |
| targetFolder | build/generated | plugin, replaces config | Target folder to generate code to. If left empty, it will print to stdout. (File, default `null`) |
| idType | integer | plugin | JsonSchema type for id fields and parameters. (String, default `string`) |
| targetFile | ./openapi.yml | plugin | Target file (String, default `openapi.yml`) |

| Option | Value | From |
| --- | --- | --- |
| --force |  | config |
| --profile | test | plugin, replaces config |

### ZDLToAsyncAPIPlugin

Declared `(inherit false)`: it inherits nothing from the `config` block.

Generates a draft AsyncAPI file with events from your ZDL services.

#### Own configuration

| Property | Value | Description |
| --- | --- | --- |
| schemaFormat | avro | Schema format for messages' payload (SchemaFormat, default `schema`, one of schema, avro) |
| targetFile | ./asyncapi.yml | Target file (String, default `null`) |

| Option | Value |
| --- | --- |
| --verbose |  |

#### Resolved configuration

| Property | Value | From | Description |
| --- | --- | --- | --- |
| schemaFormat | avro | plugin | Schema format for messages' payload (SchemaFormat, default `schema`, one of schema, avro) |
| targetFile | ./asyncapi.yml | plugin | Target file (String, default `null`) |

| Option | Value | From |
| --- | --- | --- |
| --verbose |  | plugin |

### ZdlToMarkdownPlugin (disabled)

Disabled: it does not run.

Generates Markdown glossary from Zdl Models

#### Own configuration

| Property | Value | Description |
| --- | --- | --- |
| targetFile | ./docs.md | Target file (String, default `zdl-glossary.md`) |

#### Resolved configuration

| Property | Value | From | Description |
| --- | --- | --- | --- |
| id | urn:com.arcadiaeditions:orders:checkout:scripts | config |  |
| title | Arcadia Editions - Orders Checkout scripts | config |  |
| basePackage | io.example.orders | config |  |
| targetFolder | . | config |  |
| targetFile | ./docs.md | plugin | Target file (String, default `zdl-glossary.md`) |

| Option | Value | From |
| --- | --- | --- |
| --force |  | config |
| --profile | local build | config |

### TeamOwnPlugin

This plugin is not in the plugin catalogue, so it has no description.

#### Own configuration

| Property | Value | Description |
| --- | --- | --- |
| maxDepth | 3 |  |
| paths | src/a,src/b |  |

#### Resolved configuration

| Property | Value | From | Description |
| --- | --- | --- | --- |
| id | urn:com.arcadiaeditions:orders:checkout:scripts | config |  |
| title | Arcadia Editions - Orders Checkout scripts | config |  |
| basePackage | io.example.orders | config |  |
| targetFolder | . | config |  |
| maxDepth | 3 | plugin |  |
| paths | src/a,src/b | plugin |  |

| Option | Value | From |
| --- | --- | --- |
| --force |  | config |
| --profile | local build | config |

