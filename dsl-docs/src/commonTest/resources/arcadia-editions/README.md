# Arcadia Editions fixtures

Copies of public sample files, used as test input. Do not edit them; copy them again when the upstream files change.

- `orders-checkout-api/`: https://github.com/arcadia-editions/orders-checkout-api at `226424a`
  (`domain-model.zdl`, `openapi.yml`, `asyncapi.yml`, `asyncapi-client.yml`, `avro/*.avsc`). The Avro schemas are read through
  the `$ref`s of `asyncapi.yml`; `asyncapi.yml` was refreshed from the upstream working copy (version 0.0.1).
- `business-flows/place-order-flow.zfl`: https://github.com/arcadia-editions/arcadia-editions-architecture at `83b9aa1`.
