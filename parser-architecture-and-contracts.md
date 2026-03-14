# ZFL/ZDL Parser - Architecture and Contracts

## 1. Purpose

The parser project is the canonical model producer for the ZenWave DSL ecosystem. Its job is to accept source text for two DSLs and return structured models that other tools can consume without re-implementing grammar rules:

- `ZFL`: flow modeling for Event Storming timelines
- `ZDL`: domain modeling for bounded contexts, aggregates, services, events, APIs, and related concepts

In current reality, the repository provides:

1. raw parse models for both languages
2. semantic normalization for ZFL
3. flow graph generation for ZFL
4. validation and post-processing for ZDL
5. source-location side tables for both DSLs

The intended ecosystem role is:

- the parser owns syntax interpretation and model generation
- the language server owns LSP transport and workspace concerns
- the IntelliJ plugin owns editor UX, tree views, diagrams, and navigation

The plugin agent should treat this parser as the source of truth for language understanding. It should not infer language meaning from text patterns if parser outputs are available.

## 2. Supported Languages

### ZFL

ZFL is the flow-modeling DSL used for Event Storming style timelines. It models temporal flow logic: starts, triggers, policies, commands, events, systems, services, and outcomes.

Typical constructs in current grammar and model output:

- `systems`
- `system service`
- `flow`
- `start`
- `when`
- `command`
- `event`
- `end`
- options/annotations such as `@actor`, `@time`, `@if`, `@zdl`

Example from the checked-in sample:

```zfl
systems {
    @zdl("subscription/model.zdl")
    Subscription {
        service SubscriptionService
    }
}

flow PaymentsFlow {
    @actor(Customer)
    start CustomerRequestsSubscriptionRenewal { }

    when CustomerRequestsSubscriptionRenewal {
        service Subscription.SubscriptionService
        command renewSubscription
        event SubscriptionRenewed
    }

    end {
        completed: PaymentRecorded
    }
}
```

Internal representation in current reality:

- raw parse output: `ZflModel`, a mutable map-backed structure
- normalized semantic output: `ZflSemanticModel`
- diagram output: `FlowViewModel`

The raw ZFL model contains:

- `imports`
- `config`
- `systems`
- `flows`
- `locations`
- `problems`

The semantic model makes the flow concepts explicit:

- `flows`
- `systems`
- `actors`
- `diagnostics`

### ZDL

ZDL is the domain-modeling DSL used for bounded context and domain structure modeling.

Typical constructs supported by the parser:

- `config`
- `apis`
- `aggregates`
- `entities`
- `enums`
- `relationships`
- `services`
- `inputs`
- `outputs`
- `events`
- annotations/options such as `@aggregate`, `@rest`, `@asyncapi`, `@copy`, `@input`

Representative example from the checked-in sample:

```zdl
apis {
    asyncapi(provider) default {
        uri "orders/src/main/resources/apis/asyncapi.yml"
    }
}

aggregate CustomerOrderAggregate(CustomerOrder) {
    customerOrderCommand(CustomerOrderInput) withEvents OrderEvent
}

@aggregate
entity CustomerOrder {
    status OrderStatus required
}

@rest("/orders")
service OrdersService for (CustomerOrder) {
    @asyncapi({channel: "CancelOrdersChannel", topic: "orders.cancel_orders"})
    cancelOrder(id, CancelOrderInput) CustomerOrder withEvents OrderEvent OrderStatusUpdated
}

@asyncapi({channel: "OrdersChannel", topic: "orders.orders"})
event OrderEvent {
   id String
}
```

Internal representation in current reality:

- raw parse output: `ZdlModel`, a mutable map-backed structure
- post-processing enriches field metadata such as `isEntity`, `isEnum`, `isInput`, `isOutput`, `isEvent`, `isComplexType`
- validation populates `problems`

The top-level ZDL model contains:

- `imports`
- `config`
- `apis`
- `aggregates`
- `entities`
- `enums`
- `relationships`
- `services`
- `inputs`
- `outputs`
- `events`
- `locations`
- `problems`
- `allEntitiesAndEnums` after post-processing

## 3. Technology Architecture

### Kotlin Multiplatform architecture

The project is Kotlin Multiplatform with shared parser logic in `commonMain` and platform-specific runtime details in `jvmMain` and `jsMain`.

Shared infrastructure:

- ANTLR grammar definitions for both DSLs
- parse listeners
- raw model builders
- ZFL semantic analyzer
- ZFL flow graph transformer
- common serialization-friendly data classes for semantic and graph outputs

Platform-specific behavior:

- JVM: Java interop and real ELK-based graph layout
- JS/Node: plain JS exports for raw parsing, plus fallback flow layout implementation

### JVM runtime

The JVM runtime is the richest target and the best current fit for IntelliJ integration.

It supports:

- `ZdlParser`
- `ZflParser`
- ZDL post-processing and validation
- ZFL semantic analysis
- ZFL graph generation
- ELK-based layout through `ElkFlowLayoutEngine`

### Node.js runtime

The JS target exports:

- `parseZdl(input: String): Any?`
- `parseZfl(input: String): Any?`

Current reality:

- Node export covers raw parse models only
- semantic ZFL model and flow graph are not exported as JS APIs in this repository
- JS flow layout delegates to a fallback pure Kotlin layout engine, not ELK.js

### How parsing is executed

Both DSLs follow the same runtime pattern:

1. caller provides full source text as a `String`
2. parser builds an ANTLR lexer
3. parser builds an ANTLR parser over a `CommonTokenStream`
4. parser walks the parse tree with a listener
5. listener fills a mutable map-backed model
6. optional post-processing / validation / semantic transformation runs after raw parse

### Main modules/components

ZFL path:

- `ZflParser`
- `ZflListenerImpl`
- `ZflModel`
- `ZflSemanticAnalyzer`
- `ZflSemanticModel`
- `ZflToFlowViewModelTransformer`
- `FlowViewModel`
- `ElkFlowLayoutEngine`

ZDL path:

- `ZdlParser`
- `ZdlListenerImpl`
- `ZdlModel`
- `ZdlModelPostProcessor`
- `ZdlModelValidator`

### Shared vs separate parsers

The two DSLs share infrastructure patterns, but they are separate parsers with separate grammars and listeners.

Shared:

- ANTLR-based parsing approach
- map-backed raw model architecture
- `locations` side-table mechanism
- options/annotation parsing style
- JS export pattern

Separate:

- grammar files
- parser classes
- listener implementations
- raw model top-level schema
- downstream enrichment logic

## 4. Parsing Pipeline

The full pipeline differs slightly between ZFL and ZDL.

### 4.1 Input source

Current parser API accepts raw text, not files:

```kotlin
val zdlModel = ZdlParser().parseModel(zdlText)
val zflModel = ZflParser().parseModel(zflText)
```

The caller is responsible for:

- file I/O
- unsaved editor buffer access
- URI tracking
- document versioning

### 4.2 Lexical analysis

ANTLR lexers tokenize the incoming text:

- `ZdlLexer`
- `ZflLexer`

### 4.3 Syntax parsing

ANTLR parsers create parse trees:

- `io.zenwave360.language.antlr.ZdlParser`
- `io.zenwave360.language.antlr.ZflParser`

The public parser classes then walk those trees with listeners.

### 4.4 AST creation

Current reality:

- the repository does not expose a separate typed AST contract
- the parse tree is ANTLR internal state
- the public result of tree walking is a map-backed model, not a public AST object graph

For integration purposes:

- treat `ZdlModel` and `ZflModel` as the public structural parse output
- do not depend on ANTLR parse-tree types from external consumers

### 4.5 Semantic normalization

ZFL semantic normalization:

- `ZflSemanticAnalyzer` reads `ZflModel`
- emits `ZflSemanticModel`
- derives flows, actors, systems, commands, events, policies, ends

ZDL semantic normalization:

- there is no separate typed semantic model class today
- `ZdlModelPostProcessor` enriches the raw map model
- `ZdlModelValidator` adds semantic validation problems

### 4.6 Model generation

ZFL model outputs:

1. raw `ZflModel`
2. typed `ZflSemanticModel`
3. typed `FlowViewModel`
4. optional layout-enriched `FlowViewModel`

ZDL model outputs:

1. raw but enriched `ZdlModel`
2. validation `problems`
3. `locations`

## 5. Output Models

### 5.1 ZFL outputs

#### Raw structural model: `ZflModel`

Top-level shape:

```json
{
  "imports": [],
  "config": {},
  "systems": {},
  "flows": {},
  "locations": {},
  "problems": []
}
```

#### Semantic model: `ZflSemanticModel`

Top-level shape:

```json
{
  "flows": [],
  "systems": {},
  "actors": {},
  "diagnostics": []
}
```

#### Diagram graph model: `FlowViewModel`

Top-level shape:

```json
{
  "schema": "zfl.eventflow.view@1",
  "nodes": [],
  "edges": [],
  "layout": null,
  "bounds": null,
  "systemGroups": null
}
```

### 5.2 ZDL outputs

#### Domain model structure: `ZdlModel`

Top-level shape:

```json
{
  "imports": [],
  "config": {},
  "apis": {},
  "aggregates": {},
  "entities": {},
  "enums": {},
  "relationships": {},
  "services": {},
  "inputs": {},
  "outputs": {},
  "events": {},
  "locations": {},
  "problems": []
}
```

After post-processing, an additional derived map is present:

```json
{
  "allEntitiesAndEnums": {}
}
```

### 5.3 Conceptual hierarchy model

Current reality:

- there is no single first-class `ConceptualHierarchy` type in the repository
- the plugin must build the conceptual tree from parser outputs

Recommended source of truth by language:

- ZFL hierarchy: build from `ZflSemanticModel`
- ZDL hierarchy: build from `ZdlModel`

### 5.4 Diagnostics

Current reality differs by DSL:

- ZDL: semantic validation problems are populated in `ZdlModel.problems`
- ZFL: diagnostics infrastructure exists, but typed semantic diagnostics are not meaningfully populated yet
- both DSLs maintain `locations`, but syntax errors are not surfaced through a stable structured API

### 5.5 Source metadata

Both DSLs expose source metadata through:

- `locations` side tables in raw models
- `SourceRef` in ZFL semantic/graph models

These surfaces are not yet fully aligned.

## 6. Diagram Graph Contract (ZFL)

The canonical graph contract is `FlowViewModel`.

### 6.1 Top-level shape

```json
{
  "schema": "zfl.eventflow.view@1",
  "nodes": [],
  "edges": [],
  "layout": null,
  "bounds": null,
  "systemGroups": null
}
```

### 6.2 Nodes

`FlowNode` fields:

| Field | Type | Required | Notes |
|---|---|---:|---|
| `id` | `string` | yes | Deterministic within a graph |
| `type` | `START \| COMMAND \| EVENT \| POLICY \| END` | yes | Semantic node type |
| `label` | `string` | yes | UI label |
| `system` | `string \| null` | yes | Owning system |
| `service` | `string \| null` | yes | Owning service |
| `sourceRef` | `SourceRef` | yes | Current best-effort source origin |
| `position` | `{x:number,y:number} \| null` | yes | Null before layout |
| `dimensions` | `{width:number,height:number} \| null` | yes | Null before layout |

Node type enum:

```json
["START", "COMMAND", "EVENT", "POLICY", "END"]
```

### 6.3 Edges

`FlowEdge` fields:

| Field | Type | Required | Notes |
|---|---|---:|---|
| `id` | `string` | yes | Deterministic within a graph |
| `source` | `string` | yes | Source node id |
| `target` | `string` | yes | Target node id |
| `type` | `CAUSATION \| TRIGGER \| CONDITIONAL \| ERROR` | yes | Semantic edge meaning |
| `label` | `string \| null` | yes | Optional display label |
| `sourceRef` | `SourceRef \| null` | yes | Current best-effort source origin |

Edge type enum:

```json
["CAUSATION", "TRIGGER", "CONDITIONAL", "ERROR"]
```

### 6.4 Metadata

Layout metadata:

```json
{
  "engine": "elk-layered",
  "direction": "LR",
  "rankSpacing": 80.0,
  "nodeSpacing": 120.0
}
```

Bounds metadata:

```json
{
  "x": 0.0,
  "y": 0.0,
  "width": 2220.0,
  "height": 632.0
}
```

System group metadata:

```json
{
  "systemName": "Subscription",
  "bounds": {
    "x": 380.0,
    "y": 380.0,
    "width": 1640.0,
    "height": 272.0
  }
}
```

### 6.5 Stable ID rules

Current transformer rules:

- event node: `event:${eventName}`
- command node: `command:${commandName}`
- end node: `end:${outcomeKey}`
- policy node: `policy:${triggersCommaJoined}:${commandName}`
- edge: `from[${sourceId}]to[${targetId}]`

Important caveats:

- IDs are deterministic but not globally namespaced
- IDs do not include document URI
- IDs do not include flow name
- `START` nodes currently use event-style IDs, not `start:*`

### 6.6 Example graph JSON

```json
{
  "schema": "zfl.eventflow.view@1",
  "nodes": [
    {
      "id": "event:CustomerRequestsSubscriptionRenewal",
      "type": "START",
      "label": "CustomerRequestsSubscriptionRenewal",
      "system": null,
      "service": null,
      "sourceRef": {
        "file": "<zfl>",
        "line": 1,
        "column": 1
      },
      "position": null,
      "dimensions": null
    },
    {
      "id": "policy:CustomerRequestsSubscriptionRenewal:renewSubscription",
      "type": "POLICY",
      "label": "when CustomerRequestsSubscriptionRenewal do renewSubscription",
      "system": null,
      "service": null,
      "sourceRef": {
        "file": "<zfl>",
        "line": 1,
        "column": 1
      },
      "position": null,
      "dimensions": null
    },
    {
      "id": "command:renewSubscription",
      "type": "COMMAND",
      "label": "renewSubscription",
      "system": "Subscription",
      "service": "SubscriptionService",
      "sourceRef": {
        "file": "<zfl>",
        "line": 1,
        "column": 1
      },
      "position": null,
      "dimensions": null
    },
    {
      "id": "event:SubscriptionRenewed",
      "type": "EVENT",
      "label": "SubscriptionRenewed",
      "system": "Subscription",
      "service": "SubscriptionService",
      "sourceRef": {
        "file": "<zfl>",
        "line": 1,
        "column": 1
      },
      "position": null,
      "dimensions": null
    }
  ],
  "edges": [
    {
      "id": "from[event:CustomerRequestsSubscriptionRenewal]to[policy:CustomerRequestsSubscriptionRenewal:renewSubscription]",
      "source": "event:CustomerRequestsSubscriptionRenewal",
      "target": "policy:CustomerRequestsSubscriptionRenewal:renewSubscription",
      "type": "TRIGGER",
      "label": null,
      "sourceRef": {
        "file": "<zfl>",
        "line": 1,
        "column": 1
      }
    },
    {
      "id": "from[policy:CustomerRequestsSubscriptionRenewal:renewSubscription]to[command:renewSubscription]",
      "source": "policy:CustomerRequestsSubscriptionRenewal:renewSubscription",
      "target": "command:renewSubscription",
      "type": "TRIGGER",
      "label": null,
      "sourceRef": {
        "file": "<zfl>",
        "line": 1,
        "column": 1
      }
    },
    {
      "id": "from[command:renewSubscription]to[event:SubscriptionRenewed]",
      "source": "command:renewSubscription",
      "target": "event:SubscriptionRenewed",
      "type": "CAUSATION",
      "label": null,
      "sourceRef": {
        "file": "<zfl>",
        "line": 1,
        "column": 1
      }
    }
  ],
  "layout": null,
  "bounds": null,
  "systemGroups": null
}
```

## 7. Conceptual Hierarchy Contract

Current reality:

- the repository does not emit one dedicated conceptual-tree contract
- the plugin must derive a tree from parser outputs

Recommended canonical hierarchy contract for plugin-side normalization:

```json
{
  "schema": "zenwave.conceptual.hierarchy@1",
  "documents": [
    {
      "uri": "file:///workspace/orders.zdl",
      "language": "zdl",
      "children": []
    },
    {
      "uri": "file:///workspace/subscriptions.zfl",
      "language": "zfl",
      "children": []
    }
  ]
}
```

Proposed node shape:

| Field | Type | Required |
|---|---|---:|
| `id` | `string` | yes |
| `kind` | `string` | yes |
| `name` | `string` | yes |
| `language` | `zfl \| zdl` | yes |
| `source` | `SourceLocation` | yes |
| `children` | `HierarchyNode[]` | yes |
| `references` | `Reference[]` | no |
| `metadata` | `object` | no |

### 7.1 ZFL hierarchy example

Recommended tree built from `ZflSemanticModel`:

```json
{
  "id": "file:///workspace/subscriptions.zfl#PaymentsFlow",
  "kind": "flow",
  "name": "PaymentsFlow",
  "language": "zfl",
  "source": {
    "uri": "file:///workspace/subscriptions.zfl",
    "range": {
      "start": { "line": 11, "character": 0 },
      "end": { "line": 91, "character": 1 }
    }
  },
  "children": [
    {
      "id": "file:///workspace/subscriptions.zfl#PaymentsFlow/start/CustomerRequestsSubscriptionRenewal",
      "kind": "start",
      "name": "CustomerRequestsSubscriptionRenewal",
      "language": "zfl",
      "source": {
        "uri": "file:///workspace/subscriptions.zfl",
        "range": {
          "start": { "line": 14, "character": 4 },
          "end": { "line": 18, "character": 5 }
        }
      },
      "children": []
    },
    {
      "id": "file:///workspace/subscriptions.zfl#PaymentsFlow/policy/renewSubscription",
      "kind": "policy",
      "name": "when CustomerRequestsSubscriptionRenewal do renewSubscription",
      "language": "zfl",
      "source": {
        "uri": "file:///workspace/subscriptions.zfl",
        "range": {
          "start": { "line": 32, "character": 4 },
          "end": { "line": 36, "character": 5 }
        }
      },
      "children": []
    }
  ]
}
```

### 7.2 ZDL hierarchy example

Recommended tree built from `ZdlModel`:

```json
{
  "id": "file:///workspace/orders.zdl",
  "kind": "document",
  "name": "orders.zdl",
  "language": "zdl",
  "source": {
    "uri": "file:///workspace/orders.zdl",
    "range": {
      "start": { "line": 0, "character": 0 },
      "end": { "line": 281, "character": 1 }
    }
  },
  "children": [
    {
      "id": "file:///workspace/orders.zdl#apis",
      "kind": "section",
      "name": "apis",
      "language": "zdl",
      "source": {
        "uri": "file:///workspace/orders.zdl",
        "range": {
          "start": { "line": 61, "character": 0 },
          "end": { "line": 71, "character": 1 }
        }
      },
      "children": [
        {
          "id": "file:///workspace/orders.zdl#api/default",
          "kind": "api",
          "name": "default",
          "language": "zdl",
          "source": {
            "uri": "file:///workspace/orders.zdl",
            "range": {
              "start": { "line": 62, "character": 4 },
              "end": { "line": 64, "character": 5 }
            }
          },
          "children": []
        }
      ]
    }
  ]
}
```

## 8. ZDL Domain Model Contract

The ZDL contract is currently the enriched `ZdlModel` map structure.

### 8.1 Top-level keys

```json
{
  "imports": [],
  "config": {},
  "apis": {},
  "aggregates": {},
  "entities": {},
  "enums": {},
  "relationships": {},
  "services": {},
  "inputs": {},
  "outputs": {},
  "events": {},
  "locations": {},
  "problems": [],
  "allEntitiesAndEnums": {}
}
```

### 8.2 Aggregate

Current shape:

```json
{
  "name": "CustomerOrderAggregate",
  "type": "aggregates",
  "className": "CustomerOrderAggregate",
  "javadoc": null,
  "aggregateRoot": "CustomerOrder",
  "commands": {
    "customerOrderCommand": {
      "name": "customerOrderCommand",
      "aggregateName": "CustomerOrderAggregate",
      "parameter": "CustomerOrderInput",
      "parameterIsOptional": false,
      "withEvents": ["OrderEvent"],
      "javadoc": null
    }
  }
}
```

### 8.3 Entity

Current shape:

```json
{
  "name": "CustomerOrder",
  "className": "CustomerOrder",
  "tableName": "customer_order",
  "instanceName": "customerOrder",
  "classNamePlural": "CustomerOrders",
  "instanceNamePlural": "customerOrders",
  "kebabCase": "customer-order",
  "kebabCasePlural": "customer-orders",
  "javadoc": null,
  "options": {
    "aggregate": true
  },
  "fields": {
    "status": {
      "name": "status",
      "type": "OrderStatus",
      "initialValue": "OrderStatus.RECEIVED",
      "javadoc": null,
      "comment": null,
      "isEnum": true,
      "isEntity": false,
      "isArray": false,
      "isComplexType": true,
      "options": {},
      "validations": {
        "required": {
          "name": "required",
          "value": ""
        }
      }
    }
  },
  "type": "entities",
  "optionsList": [
    {
      "name": "aggregate",
      "value": true
    }
  ]
}
```

### 8.4 Enum

Current shape:

```json
{
  "name": "OrderStatus",
  "type": "enums",
  "className": "OrderStatus",
  "javadoc": null,
  "comment": null,
  "values": {
    "RECEIVED": {
      "name": "RECEIVED",
      "javadoc": null,
      "comment": null,
      "value": null
    }
  }
}
```

Enum with explicit values:

```json
{
  "name": "EnumWithValue",
  "type": "enums",
  "className": "EnumWithValue",
  "hasValue": true,
  "values": {
    "VALUE1": {
      "name": "VALUE1",
      "value": "1"
    }
  }
}
```

### 8.5 Relationship

Relationships are grouped by relationship type under `relationships`.

Current shape:

```json
{
  "type": "ManyToOne",
  "name": "ManyToOne_Address{customer}_Customer",
  "from": "Address",
  "injectedFieldInFrom": "customer",
  "fromOptions": {},
  "fromValidations": {},
  "commentInFrom": "Address.customer javadoc",
  "to": "Customer",
  "injectedFieldInTo": null,
  "toOptions": {},
  "toValidations": {}
}
```

### 8.6 Service

Current shape:

```json
{
  "name": "OrdersService",
  "className": "OrdersService",
  "javadoc": null,
  "aggregates": ["CustomerOrder"],
  "methods": {
    "cancelOrder": {
      "name": "cancelOrder",
      "serviceName": "OrdersService",
      "paramId": "id",
      "paramIdIsOptional": false,
      "parameter": "CancelOrderInput",
      "parameterIsOptional": false,
      "returnType": "CustomerOrder",
      "returnTypeIsArray": false,
      "returnTypeIsOptional": false,
      "withEvents": ["OrderEvent", "OrderStatusUpdated"],
      "javadoc": null,
      "options": {
        "asyncapi": {
          "channel": "CancelOrdersChannel",
          "topic": "orders.cancel_orders"
        }
      }
    }
  },
  "options": {
    "rest": "/orders"
  },
  "optionsList": [
    {
      "name": "rest",
      "value": "/orders"
    }
  ]
}
```

### 8.7 Event

Current shape:

```json
{
  "name": "OrderEvent",
  "className": "OrderEvent",
  "type": "events",
  "kebabCase": "order-event",
  "javadoc": "OrderEvent javadoc",
  "fields": {
    "id": {
      "name": "id",
      "type": "String"
    }
  },
  "options": {
    "copy": "CustomerOrder",
    "asyncapi": {
      "channel": "OrdersChannel",
      "topic": "orders.orders"
    }
  },
  "optionsList": [
    {
      "name": "copy",
      "value": "CustomerOrder"
    },
    {
      "name": "asyncapi",
      "value": {
        "channel": "OrdersChannel",
        "topic": "orders.orders"
      }
    }
  ]
}
```

### 8.8 Inputs and outputs

Inputs and outputs use the same entity-like structure as entities:

- naming metadata
- `fields`
- `options`
- nested complex type enrichment

### 8.9 Annotations / options contract

Annotations are represented as:

- `options: { [name]: value }`
- `optionsList: [{ "name": "...", "value": ... }]`

This applies to entities, services, events, inputs, outputs, and other annotated elements.

Examples:

```json
{
  "options": {
    "aggregate": true
  }
}
```

```json
{
  "options": {
    "rest": "/orders"
  }
}
```

```json
{
  "options": {
    "asyncapi": {
      "api": "RestaurantsAsyncAPI",
      "channel": "KitchenOrdersStatusChannel"
    }
  }
}
```

## 9. Cross References

Cross references are important for the plugin, but current reality is mixed.

### 9.1 ZFL flows -> ZDL elements

Current reality:

- ZFL raw/semantic models do not emit a first-class resolved-reference object
- references are implicit by name, mostly through:
  - system names
  - service names
  - command names
  - event names
  - `@zdl` system options

Example implicit reference in raw ZFL model:

```json
{
  "flows": {
    "PaymentsFlow": {
      "whens": [
        {
          "system": "Subscription",
          "service": "SubscriptionService",
          "command": "renewSubscription",
          "events": ["SubscriptionRenewed"]
        }
      ]
    }
  },
  "systems": {
    "Subscription": {
      "options": {
        "zdl": "subscription/model.zdl"
      }
    }
  }
}
```

Integration rule:

- resolve ZFL command/event/service references by matching names against the relevant ZDL model
- use `systems.*.options.zdl` as the declared boundary from flow system to ZDL source/model

### 9.2 ZFL flows -> APIs

Current reality:

- there is no dedicated ZFL API-reference model in the checked-in ZFL sample output
- any API linkage would currently need to be introduced through annotations/options or external resolution logic

Recommended normalized plugin contract:

```json
{
  "kind": "flow-to-api",
  "from": {
    "uri": "file:///workspace/subscriptions.zfl",
    "elementId": "command:chargePayment"
  },
  "to": {
    "uri": "file:///workspace/orders.zdl",
    "elementPath": "apis.RestaurantsAsyncAPI"
  },
  "resolution": "derived"
}
```

### 9.3 ZDL events -> messaging channels

This is explicitly represented today through event annotations/options.

Example:

```json
{
  "name": "OrderStatusUpdated",
  "type": "events",
  "options": {
    "asyncapi": {
      "channel": "OrderUpdatesChannel",
      "topic": "orders.order_updates"
    }
  }
}
```

### 9.4 ZDL service methods -> APIs / channels

Service method annotations also encode messaging and API references.

Example:

```json
{
  "name": "updateKitchenStatus",
  "options": {
    "asyncapi": {
      "api": "RestaurantsAsyncAPI",
      "channel": "KitchenOrdersStatusChannel"
    }
  }
}
```

### 9.5 Recommended normalized reference shape

Because current outputs are mostly implicit or annotation-based, the plugin should normalize them into one reference shape:

```json
{
  "schema": "zenwave.references@1",
  "kind": "zdl-service-method-to-api",
  "source": {
    "uri": "file:///workspace/orders.zdl",
    "elementPath": "services.OrdersService.methods.updateKitchenStatus"
  },
  "target": {
    "uri": "file:///workspace/orders.zdl",
    "elementPath": "apis.RestaurantsAsyncAPI"
  },
  "metadata": {
    "channel": "KitchenOrdersStatusChannel"
  }
}
```

## 10. Source Location Contract

### 10.1 Current reality

Both raw models expose a `locations` side table keyed by model path. Each location is an `int[6]`:

| Index | Meaning |
|---|---|
| `0` | start offset |
| `1` | end offset exclusive |
| `2` | start line, 1-based |
| `3` | start column, 0-based |
| `4` | end line, 1-based |
| `5` | end column, 0-based or derived end column |

Example from ZDL:

```json
{
  "path": "services.OrdersService.methods.cancelOrder.parameter",
  "location": [6975, 6991, 227, 20, 227, 36]
}
```

ZFL semantic and graph outputs also expose `SourceRef`:

```json
{
  "file": "<zfl>",
  "line": 1,
  "column": 1
}
```

Current reality caveat:

- `SourceRef` is not precise enough for editor navigation today
- `locations` is the high-precision source surface

### 10.2 Canonical integration contract

For IntelliJ and LSP integrations, normalize parser locations immediately to URI + range + offsets:

```json
{
  "uri": "file:///workspace/orders.zdl",
  "range": {
    "start": { "line": 226, "character": 20 },
    "end": { "line": 226, "character": 36 }
  },
  "offsets": {
    "start": 6975,
    "endExclusive": 6991
  }
}
```

### 10.3 Normalization rules

- convert parser lines from 1-based to 0-based
- keep columns as-is from `locations`
- preserve offsets for direct IntelliJ `TextRange` mapping
- do not trust ZFL `SourceRef` as canonical until it is resolved from `locations`

## 11. Diagnostics Contract

### 11.1 Current reality

Diagnostics are asymmetric today.

ZDL:

- semantic validation is implemented
- issues are reported in `ZdlModel.problems`

ZFL:

- `ZflSemanticModel.diagnostics` exists as a type
- current analyzer does not populate it meaningfully
- no stable structured syntax-error contract is exposed

### 11.2 Current raw problem shape

For ZDL and shared model infrastructure:

```json
{
  "path": "services.OrdersService.methods.cancelOrder.parameter",
  "location": [6975, 6991, 227, 20, 227, 36],
  "value": "UnknownInput",
  "message": "UnknownInput is not an entity or input"
}
```

Fields:

| Field | Type | Required |
|---|---|---:|
| `path` | `string` | yes |
| `location` | `int[6] \| null` | yes |
| `value` | `string \| null` | yes |
| `message` | `string` | yes |

### 11.3 Typed ZFL diagnostic shape

Current type:

```json
{
  "message": "Unknown system 'Payments'",
  "severity": "ERROR",
  "sourceRef": {
    "file": "<zfl>",
    "line": 1,
    "column": 1
  }
}
```

Fields:

| Field | Type | Required |
|---|---|---:|
| `message` | `string` | yes |
| `severity` | `INFO \| WARNING \| ERROR` | yes |
| `sourceRef` | `SourceRef \| null` | yes |

### 11.4 Recommended normalized diagnostic contract

The plugin and language server should normalize all parser diagnostics to one shape:

```json
{
  "schema": "zenwave.diagnostics@1",
  "message": "UnknownInput is not an entity or input",
  "severity": "ERROR",
  "code": "zdl.validation.invalid-parameter-type",
  "recoverable": true,
  "location": {
    "uri": "file:///workspace/orders.zdl",
    "range": {
      "start": { "line": 226, "character": 20 },
      "end": { "line": 226, "character": 36 }
    }
  },
  "sourcePath": "services.OrdersService.methods.cancelOrder.parameter"
}
```

## 12. Integration Guidance for IntelliJ Plugin

The plugin should consume parser outputs as contracts and keep language knowledge centralized in the parser project.

### 12.1 Recommended model usage

Use these parser outputs as the canonical backing data:

- tree view:
  - ZFL: `ZflSemanticModel`
  - ZDL: `ZdlModel`
- diagram rendering:
  - ZFL: `FlowViewModel`
- navigation:
  - `locations` side tables normalized to URI/range/offset form
- cross references:
  - ZFL semantic/raw names resolved against `ZdlModel`
  - ZDL annotation/options resolved from `options` / `optionsList`

### 12.2 What the plugin should not duplicate

- ANTLR grammar logic
- flow semantic extraction
- domain model parsing rules
- annotation parsing
- graph derivation

### 12.3 Suggested plugin pipeline

1. obtain current document text from editor buffer
2. call the correct parser by file type
3. cache parser output by document version
4. normalize `locations` to editor ranges
5. build conceptual tree from parser models
6. for `.zfl`, optionally build graph via semantic model -> `FlowViewModel`
7. resolve cross references against loaded `.zdl` models

### 12.4 Practical warning

For navigation and highlighting, the plugin should prefer raw-model `locations` over ZFL `SourceRef`. The latter is currently best-effort and not editor-grade.

## 13. Integration Guidance for Language Server

The language server should treat the parser as the single-document analysis backend.

Responsibilities that should stay in the language server:

- document synchronization
- URI ownership
- caching by version
- workspace-wide symbol indexing
- conversion to LSP `Diagnostic`, `DocumentSymbol`, `Location`, `CodeLens`, and references

Recommended server pipeline:

1. receive current document text
2. parse to raw model
3. if ZFL, derive semantic model and optional graph
4. normalize diagnostics and locations to LSP-native form
5. cache outputs for later requests
6. resolve cross-file references using workspace model

What the server should not do:

- re-parse DSL grammar independently
- re-derive flow graph semantics independently
- infer ZDL constructs by text scanning

## 14. Versioning and Compatibility

### Current reality

Only the ZFL graph contract carries an explicit schema string:

```json
{
  "schema": "zfl.eventflow.view@1"
}
```

Other outputs do not currently carry explicit version fields.

### Recommended policy

- treat `FlowViewModel.schema` as the compatibility gate for graph consumers
- introduce explicit `schema` or `contractVersion` fields for:
  - conceptual hierarchy normalization
  - diagnostic normalization
  - cross-reference normalization
- keep additive changes within a schema version
- bump schema when:
  - field names change
  - nullability changes
  - enum values change
  - ID rules change
  - location format changes

### Known compatibility risks

- ZFL raw model, semantic model, and graph model are not versioned uniformly
- ZDL model is map-backed, so field additions are easy but breaking renames are hard to detect
- source-location surfaces are inconsistent between raw and semantic outputs
- some checked-in samples reflect older graph conventions

## 15. Open Questions

1. Should ZDL gain a dedicated typed semantic model, instead of only an enriched map-backed raw model?
2. Should the parser emit a first-class conceptual hierarchy contract rather than requiring plugin-side normalization?
3. Should cross references become explicit parser outputs instead of name-based or annotation-based resolution?
4. Should ZFL semantic diagnostics be implemented to parity with ZDL validation problems?
5. Should syntax errors be exposed as a stable structured contract for both DSLs?
6. Should ZFL `SourceRef` evolve to URI + range + offsets and become authoritative?
7. Should `FlowViewModel` IDs be namespaced by flow name or document URI?
8. Should JS exports include ZFL semantic model and flow graph generation, not just raw parse output?
9. Should ZFL gain explicit API-reference constructs if flows are expected to reference OpenAPI and AsyncAPI artifacts directly?
10. Should the plugin and language server standardize on normalized contracts with explicit schemas, even if the parser continues to emit raw map-backed models internally?
