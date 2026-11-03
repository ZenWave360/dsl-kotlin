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

Each plugin in the order the file declares them, with its description, the command to run, and its own and inherited configuration.

### ZDLToOpenAPIPlugin

#### Description

Generates a draft OpenAPI definitions from your ZDL entities and services.

<details open>
<summary>Command to run</summary>

    jbang zw -p ZDLToOpenAPIPlugin id=urn:com.arcadiaeditions:orders:checkout:scripts title="Arcadia Editions - Orders Checkout scripts" basePackage=io.example.orders targetFolder=build/generated idType=integer targetFile=./openapi.yml zdlFile=preview.zw --force --profile=test
</details>

<details>
<summary>Configuration</summary>

#### Own configuration

| Property | Value | Description |
| --- | --- | --- |
| idType | integer | JsonSchema type for id fields and parameters. (String, default `string`) |
| targetFolder | build/generated | Target folder to generate code to. If left empty, it will print to stdout. (File, default `null`) |
| targetFile | ./openapi.yml | Target file (String, default `openapi.yml`) |

| Option | Value |
| --- | --- |
| --profile | test |

#### Inherited configuration

| Property | Value | Description |
| --- | --- | --- |
| id | urn:com.arcadiaeditions:orders:checkout:scripts |  |
| title | Arcadia Editions - Orders Checkout scripts | API Title (String, default `null`) |
| basePackage | io.example.orders |  |

| Option | Value |
| --- | --- |
| --force |  |

</details>

### ZDLToAsyncAPIPlugin

#### Description

Generates a draft AsyncAPI file with events from your ZDL services.

<details open>
<summary>Command to run</summary>

    jbang zw -p ZDLToAsyncAPIPlugin schemaFormat=avro targetFile=./asyncapi.yml zdlFile=preview.zw --verbose
</details>

<details>
<summary>Configuration</summary>

#### Own configuration

| Property | Value | Description |
| --- | --- | --- |
| schemaFormat | avro | Schema format for messages' payload (SchemaFormat, default `schema`, one of schema, avro) |
| targetFile | ./asyncapi.yml | Target file (String, default `null`) |

| Option | Value |
| --- | --- |
| --verbose |  |

#### Inherited configuration

Declared `(inherit false)`: it inherits nothing from the `config` block.

Inherits no options from the `config` block.

</details>

### ZdlToMarkdownPlugin (disabled)

#### Description

Disabled: it does not run.

Generates Markdown glossary from Zdl Models

<details open>
<summary>Command to run</summary>

    jbang zw -p ZdlToMarkdownPlugin id=urn:com.arcadiaeditions:orders:checkout:scripts title="Arcadia Editions - Orders Checkout scripts" basePackage=io.example.orders targetFolder=. targetFile=./docs.md zdlFile=preview.zw --force --profile="local build"
</details>

<details>
<summary>Configuration</summary>

#### Own configuration

| Property | Value | Description |
| --- | --- | --- |
| targetFile | ./docs.md | Target file (String, default `zdl-glossary.md`) |

#### Inherited configuration

| Property | Value | Description |
| --- | --- | --- |
| id | urn:com.arcadiaeditions:orders:checkout:scripts |  |
| title | Arcadia Editions - Orders Checkout scripts |  |
| basePackage | io.example.orders |  |
| targetFolder | . |  |

| Option | Value |
| --- | --- |
| --force |  |
| --profile | local build |

</details>

### TeamOwnPlugin

#### Description

This plugin is not in the plugin catalogue, so it has no description.

<details open>
<summary>Command to run</summary>

    jbang zw -p TeamOwnPlugin id=urn:com.arcadiaeditions:orders:checkout:scripts title="Arcadia Editions - Orders Checkout scripts" basePackage=io.example.orders targetFolder=. maxDepth=3 paths=src/a,src/b zdlFile=preview.zw --force --profile="local build"
</details>

<details>
<summary>Configuration</summary>

#### Own configuration

| Property | Value | Description |
| --- | --- | --- |
| maxDepth | 3 |  |
| paths | src/a,src/b |  |

#### Inherited configuration

| Property | Value | Description |
| --- | --- | --- |
| id | urn:com.arcadiaeditions:orders:checkout:scripts |  |
| title | Arcadia Editions - Orders Checkout scripts |  |
| basePackage | io.example.orders |  |
| targetFolder | . |  |

| Option | Value |
| --- | --- |
| --force |  |
| --profile | local build |

</details>

