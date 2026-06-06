# ZFL: `@outcome` — Spec

## Overview

`@outcome` groups multiple `emits` statements inside a command block into a named
branch. It fixes a diagram ambiguity: today all edges out of a command look like OR
branches. `@outcome` makes AND vs OR explicit in the model and visible in the diagram.

---

## DSL

### Syntax

```zdl
when OrderCreated do authorizePayment {
    service PaymentsProcessing.PaymentsProcessingService

    @outcome("authorized")
    emits PaymentAuthorized, OrderUpdated   // AND — both emitted together

    @outcome("declined")
    emits PaymentDeclined                   // OR — alternative branch

    @outcome("failed")
    emits PaymentFailed                     // OR — alternative branch
}
```

### Rules

- `@outcome` is optional. A `do` block with no `@outcome` annotations is valid and
  renders as today — one edge per `emits`.
- `@outcome` annotates a single `emits` line. The name is a quoted string.
- Multiple events on the same `emits` line (comma-separated) belong to the same
  outcome and are emitted together (AND semantics).
- Different `@outcome` annotations in the same block are mutually exclusive
  OR branches.
- Mixing annotated and unannotated `emits` in the same block is not allowed.
  Either all `emits` have `@outcome` or none do.

---

## Eventflow Model

### Change

Add an `outcome` field to every edge object. The field is always present.
When `@outcome` is not used, it is `null`.

```json
{
    "id": "from[command:authorizePayment]to[event:PaymentAuthorized]",
    "source": "command:authorizePayment",
    "target": "event:PaymentAuthorized",
    "type": "CAUSATION",
    "label": null,
    "outcome": "authorized"
}
```

```json
{
    "id": "from[command:capturePayment]to[event:PaymentCaptured]",
    "source": "command:capturePayment",
    "target": "event:PaymentCaptured",
    "type": "CAUSATION",
    "label": null,
    "outcome": null
}
```

### No new node types

Outcome nodes are a rendering concern, not a model concern. The parser emits edges
only. The visualizer derives outcome nodes from grouped edges at render time.

### Backward compatibility

All existing edges have `outcome: null`. No existing behavior changes.

---

## Eventflow Visualizer

### When `outcome` is null (current behavior)

One edge per `emits`, direct from command node to event node. No change.

```
[authorizePayment] ──────────► (PaymentAuthorized)
                   ──────────► (PaymentDeclined)
                   ──────────► (PaymentFailed)
```

### When `outcome` is present

The renderer groups edges by `outcome` value. For each distinct outcome it
inserts a small intermediate **outcome node** between the command and the event(s).

```
                   ──[authorized]──► (PaymentAuthorized)
                                   ► (OrderUpdated)
[authorizePayment] ──[declined]────► (PaymentDeclined)
                   ──[failed]──────► (PaymentFailed)
```

- One edge from command to outcome node, labeled with the outcome name.
- One edge from outcome node to each event in that outcome group.
- If an outcome has only one event, the outcome node is still rendered
  (keeps the visual language consistent).

### Outcome node style

- Small, neutral shape — distinct from command (rectangle) and event (oval).
- Label is the outcome name.
- No system/service attribution.
- Not clickable for navigation (it has no source reference of its own).

### Mixed blocks

A command block is either fully annotated or not at all (enforced by the parser).
The visualizer does not need to handle mixed cases.

---

## Mermaid Diagrams

### Flowchart

#### Node id prefix rename (breaking fix, apply now)

Current generated flowchart uses `outcome_*` as the id prefix for event nodes:

```
outcome_PaymentAuthorized[/PaymentAuthorized/]
```

This collides with `@outcome` branch nodes which will also need `outcome_*` ids.
Rename event node ids to `event_*` before implementing `@outcome` support:

```
event_PaymentAuthorized[/PaymentAuthorized/]
```

#### Without `@outcome` (current behavior, unchanged)

```mermaid
action_authorizePayment --> event_PaymentAuthorized
action_authorizePayment --> event_PaymentDeclined
action_authorizePayment --> event_PaymentFailed
```

#### With `@outcome`

Insert an intermediate outcome node between the action and each event group.
Outcome nodes use a distinct shape — rounded rectangle `(["label"])` — to
differentiate them from event nodes (parallelogram `/label/`) and action nodes
(rectangle `["label"]`).

```mermaid
action_authorizePayment --> outcome_authorized(["authorized"])
outcome_authorized --> event_PaymentAuthorized
outcome_authorized --> event_OrderUpdated
action_authorizePayment --> event_PaymentDeclined
action_authorizePayment --> event_PaymentFailed
```

Single-event outcomes still get an outcome node for visual consistency:

```mermaid
action_authorizePayment --> outcome_authorized(["authorized"])
outcome_authorized --> event_PaymentAuthorized
action_authorizePayment --> outcome_declined(["declined"])
outcome_declined --> event_PaymentDeclined
action_authorizePayment --> outcome_failed(["failed"])
outcome_failed --> event_PaymentFailed
```

#### Outcome node classDef

Add a new `classDef` for outcome routing nodes:

```mermaid
classDef outcomeNode fill:#ede9fe,stroke:#6d28d9,color:#0f172a
class outcome_authorized outcomeNode
```

---

### Sequence Diagram

Sequence diagrams already handle branching structurally via `alt/else` blocks.
`@outcome` maps directly to the `else` branch label — no new construct needed.

#### Without `@outcome` (current behavior)

```mermaid
alt via PaymentAuthorized
    PaymentsProcessingService-->>OrdersCheckoutService: PaymentAuthorized
else via PaymentDeclined
    PaymentsProcessingService-->>InventoryService: PaymentDeclined
else via PaymentFailed
    PaymentsProcessingService-->>PaymentsProcessingService: PaymentFailed
end
```

#### With `@outcome`

The outcome name becomes the `alt/else` label. Multi-event AND outcomes list
all events sequentially inside the same branch — no structural change needed,
the sequence already implies AND within a branch.

```mermaid
alt authorized
    PaymentsProcessingService-->>OrdersCheckoutService: PaymentAuthorized
    PaymentsProcessingService-->>OrdersCheckoutService: OrderUpdated
else declined
    PaymentsProcessingService-->>InventoryService: PaymentDeclined
else failed
    PaymentsProcessingService-->>PaymentsProcessingService: PaymentFailed
end
```

#### Retry loops

The current generator unrolls retry loops into separate `else` branches, producing
near-duplicate blocks. This is a generator concern, not an `@outcome` concern.
Retry loops should be collapsed into a note rather than enumerated:

```mermaid
else failed
    PaymentsProcessingService->>PaymentsProcessingService: retryPayment
    Note over PaymentsProcessingService: retries up to N times, then PaymentRetryExhausted
end
```
