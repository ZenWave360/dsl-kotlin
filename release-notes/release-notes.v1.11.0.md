## What's Changed

Release **1.11.0** of `dsl-kotlin`, `dsl-views` and `dsl-docs` (`io.zenwave360.dsl`), and of the `@zenwave360/dsl` npm package.

## What's New

### Rules

- New `rules` keyword in ZDL (per aggregate, `rules (Order) { limit … }`) and inside ZFL flows. Each rule may carry annotations and javadoc, kept in `rules.<name>.options` and `rules.<name>.javadoc`.
- `@rules(...)` on ZFL `when`/`do` and on ZDL service methods. A name may be bare or qualified by its aggregate, quoted or not: `@rules(Order.limit)` matches `rules (Order) { limit }`. An unknown name is a warning.
- `@rules` on a ZDL method is mirrored into the `policy` option that zenwave-sdk reads, merged with `@policy` without duplicates.
- `dsl-docs` lists rules on flow and module pages, linked from each command; `dsl-views` adds each command's rules as a `%%` comment in sequence diagrams.

### EventFlow model

- Variants carry their outcome and steps, and are grouped in a tree by outcome; a tree line lays out and renders all its variants together. Variant ids stay stable across edits.
- Lanes by system or service: full-width bands in order of first appearance.
- Policies take `@title`, `@actor` and `@timer`; variant titles follow the path; sequence diagrams draw actors and timers.
- A response is a read model node; an `emits response` event hangs from its read model.
- Saved positions apply to the full flow view, all or nothing.
- The ZFL model keeps the list of `@zdl` locations of each system.

### ZFL

- New warnings: an unreachable action, a missing completed outcome, a missing service.
- `on` takes an event list; an `on` that answers ends the command; handlers must name responses of the call before them.
- `for` before a service's aggregates is optional in ZDL and ZFL.
- Source-targeted ZFL text edits (`zfl/editing`) and an additive "organize systems" mode that only adds what is missing.

### Experimental: view, timers and expected end states

- ZFL accepts, as an experiment, a `view … by` block, `timer after|at … unless …` policies and expected states on `end` lines, with single-file and ZDL cross-file linters. Timers are drawn as timer policies in flowcharts and sequences.
- `view`, `timer`, `by`, `after`, `at` and `unless` are contextual words: existing names keep working.
- This syntax may change or be removed in a later release.

### Views and docs

- The ZFL flowchart uses EventFlow shapes and colours, with read models; outcomes are parallelograms.
- Each ZFL sequence diagram names its flow and variant in a Mermaid comment.
- A class diagram for one command, with its input and output; queries in the services Event Storming.
- Model Docs: APIs, each aggregate's policies, Inputs and Outputs, "Unused entities and enums"; javadoc rendered as CommonMark.
- Scripts Docs: three sections per plugin, with the Bash command built by the library from the plugin's command arguments.
- Single-page previews of one ZDL document and of one `.zw` document with its resolved configuration.

## Behaviour changes

- `policies` in ZDL is a deprecated alias of `rules`, and `@policy` a deprecated alias of `@rules`. Both still work and fill the same objects.
- Policies with the same name under different aggregates are kept apart.
- New properties of ZFL semantic classes are appended at the end of their constructors; existing positions are unchanged.

## Fixes

- `publishedEvents` includes events emitted after a chained call.
- Chained calls run only on their response; a chained exit belongs to the enclosing command.
- A command's return type keeps its array marker in diagrams.
- An incomplete experimental block is reported as a problem instead of dropping the rest of the file.
- Single-value annotations read the same on JVM and JS.
