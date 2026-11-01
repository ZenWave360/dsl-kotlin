# Arcadia Editions - Orders Checkout

## Contents

- [Services](#services)
  - [OrdersCheckoutService](#orderscheckoutservice)
- [Aggregates](#aggregates)
  - [Order](#order)
- [Domain events](#domain-events)
- [Commands](#commands)
- [Glossary](#glossary)
  - [Aggregates](#aggregates-1)
  - [Enums](#enums)
  - [Inputs](#inputs)
  - [Domain events](#domain-events-1)

## Services

Each service with its commands (blue) and the events they emit (orange), inside the aggregate it works on.

### OrdersCheckoutService

```mermaid
---
config:
  themeVariables:
    titleColor: "#111111"
---
flowchart LR
  classDef command fill:#a7c7f2,stroke:#3b6fb6,color:#111
  classDef event fill:#ffb76b,stroke:#d9822b,color:#111
  subgraph agg_OrdersCheckoutService["service OrdersCheckoutService for Order"]
    direction LR
    cmd_startOrderCheckout["startOrderCheckout"]
    cmd_confirmOrder["confirmOrder"]
    cmd_cancelOrder["cancelOrder"]
    evt_OrderCreated["OrderCreated"]
    evt_StockUnavailable["StockUnavailable"]
    evt_OrderConfirmed["OrderConfirmed"]
    evt_OrderCancelled["OrderCancelled"]
  end
  style agg_OrdersCheckoutService fill:#fbd3ee,stroke:#c2569b,color:#111
  class cmd_startOrderCheckout command
  class cmd_confirmOrder command
  class cmd_cancelOrder command
  class evt_OrderCreated event
  class evt_StockUnavailable event
  class evt_OrderConfirmed event
  class evt_OrderCancelled event
  cmd_startOrderCheckout --> evt_OrderCreated
  cmd_startOrderCheckout --> evt_StockUnavailable
  cmd_confirmOrder --> evt_OrderConfirmed
  cmd_cancelOrder --> evt_OrderCancelled
```

## Aggregates

Each aggregate root with the entities and enums it owns, and how they relate.

### Order

```mermaid
---
config:
  theme: default
  themeVariables:
    lineColor: "#888888"
    edgeLabelBackground: "#ffffff"
    transitionColor: "#888888"
    transitionLabelColor: "#111111"
---
classDiagram
    class Order {
        <<aggregate>>
        String orderId
        OrderStatus status
        String[] items
        Instant createdAt
        Instant confirmedAt
        Instant cancelledAt
    }
    class OrderStatus {
        <<enumeration>>
        CREATED
        CONFIRMED
        CANCELLED
    }
    Order --> OrderStatus : status
    style Order fill:#fbd3ee,stroke:#333333,color:#111111
    style OrderStatus fill:#f2f2f2,stroke:#333333,color:#111111
```

#### Lifecycle

```mermaid
---
config:
  theme: default
  themeVariables:
    lineColor: "#888888"
    edgeLabelBackground: "#ffffff"
    transitionColor: "#888888"
    transitionLabelColor: "#111111"
---
stateDiagram-v2
    [*] --> CREATED : startOrderCheckout
    CREATED --> CONFIRMED : confirmOrder
    CREATED --> CANCELLED : cancelOrder
    CONFIRMED --> CANCELLED : cancelOrder
    CANCELLED --> [*]
```

Field `status`, initial `CREATED`.

## Domain events

The events this module declares, with their fields. Fields copied with `@copy` are included; field descriptions are in the glossary.

```mermaid
---
config:
  theme: default
  themeVariables:
    lineColor: "#888888"
    edgeLabelBackground: "#ffffff"
    transitionColor: "#888888"
    transitionLabelColor: "#111111"
---
classDiagram
    class OrderCreated {
        <<event>>
        String orderId
        Integer version
    }
    class StockUnavailable {
        <<event>>
        String productId
        Integer requestedQuantity
    }
    class OrderConfirmed {
        <<event>>
        String orderId
        Integer version
        Instant confirmedAt
    }
    class OrderCancelled {
        <<event>>
        String orderId
        Integer version
        Instant cancelledAt
    }
    style OrderCreated fill:#ffe0bf,stroke:#333333,color:#111111
    style StockUnavailable fill:#ffe0bf,stroke:#333333,color:#111111
    style OrderConfirmed fill:#ffe0bf,stroke:#333333,color:#111111
    style OrderCancelled fill:#ffe0bf,stroke:#333333,color:#111111
```

## Commands

The methods of the module's services, the events each one emits and what triggers it.

| Command | Input | Returns | Events | Triggered by | Description |
| --- | --- | --- | --- | --- | --- |
| startOrderCheckout | [StartOrderCheckoutInput](#startordercheckoutinput) | [Order](#order-1) | [OrderCreated](#ordercreated) \| [StockUnavailable](#stockunavailable) | `POST /orders` |  |
| confirmOrder | [ConfirmOrderInput](#confirmorderinput) | [Order](#order-1) | [OrderConfirmed](#orderconfirmed) | asyncapi `payment-authorized-event-v1` (PaymentsProcessingApi) |  |
| cancelOrder | [CancelOrderInput](#cancelorderinput) | [Order](#order-1) | [OrderCancelled](#ordercancelled) | asyncapi `stock-released-event-v1` (CatalogInventoryApi) |  |

## Glossary

Every named type of this module, by kind.

### Aggregates

#### Order

Aggregate root

| Field | Type | Description |
| --- | --- | --- |
| orderId | String |  |
| status | [OrderStatus](#orderstatus) |  |
| items | String[] |  |
| createdAt | Instant |  |
| confirmedAt | Instant |  |
| cancelledAt | Instant |  |

### Enums

#### OrderStatus

| Value | Description |
| --- | --- |
| CREATED |  |
| CONFIRMED |  |
| CANCELLED |  |

### Inputs

#### StartOrderCheckoutInput

| Field | Type | Description |
| --- | --- | --- |
| items | String[] |  |

#### ConfirmOrderInput

| Field | Type | Description |
| --- | --- | --- |
| orderId | String |  |

#### CancelOrderInput

| Field | Type | Description |
| --- | --- | --- |
| orderId | String |  |

### Domain events

#### OrderCreated

Channel: `order-created-event-v1`. Topic: `orders-checkout.order-created.event.avro.v1`.

| Field | Type | Description |
| --- | --- | --- |
| orderId | String |  |
| version | Integer |  |

#### StockUnavailable

Channel: `stock-unavailable-event-v1`. Topic: `orders-checkout.stock-unavailable.event.avro.v1`.

| Field | Type | Description |
| --- | --- | --- |
| productId | String |  |
| requestedQuantity | Integer |  |

#### OrderConfirmed

Channel: `order-confirmed-event-v1`. Topic: `orders-checkout.order-confirmed.event.avro.v1`.

| Field | Type | Description |
| --- | --- | --- |
| orderId | String |  |
| version | Integer |  |
| confirmedAt | Instant |  |

#### OrderCancelled

Channel: `order-cancelled-event-v1`. Topic: `orders-checkout.order-cancelled.event.avro.v1`.

| Field | Type | Description |
| --- | --- | --- |
| orderId | String |  |
| version | Integer |  |
| cancelledAt | Instant |  |

