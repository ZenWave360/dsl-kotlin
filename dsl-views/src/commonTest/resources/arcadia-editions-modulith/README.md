# Arcadia Editions modulith fixtures

Copies of the modulith sample ZDL files (one ZDL file = one module), used as test input for the architecture
model and diagrams. Do not edit them; copy them again when the upstream files change.

Source: `arcadia-editions-modulith-api/*.zdl` of the Arcadia Editions samples. The `@asyncapi` lines on events are
commented out upstream, so every event of this fixture is internal. Tests for external events, foreign events,
referenced modules and calls use small inline ZDLs instead.
