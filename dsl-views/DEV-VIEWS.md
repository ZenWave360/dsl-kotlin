# Dev views

A fast loop for the views: edit a `.zfl`/`.zdl` or the Kotlin generators, run one test, and see the result in a browser.
`io.zenwave360.language.dev.DevViewsExport` (jvmTest) writes the EventFlow view models and Mermaid diagrams into a
directory that the browser app watches. The app lives in `zenwave-architectural-vistas` (`pnpm dev:views`).

## Run

- IntelliJ: run `DevViewsExport.export`. To change inputs or output, set `ZENWAVE_DEV_VIEWS_INPUT` and `ZENWAVE_DEV_VIEWS_DIR`
  in the run configuration's environment variables.
- CLI (Gradle needs JDK 24):

  ```
  ./gradlew.bat :dsl-views:jvmTest --tests io.zenwave360.language.dev.DevViewsExport -PdevViewsInput=C:/path/my.zfl -PdevViewsDir=C:/path/out
  ```

| Setting | Env | System property | Gradle property | Default |
|---|---|---|---|---|
| Inputs | `ZENWAVE_DEV_VIEWS_INPUT` | `zenwave.devViews.input` | `-PdevViewsInput` | the `flow/*.zfl` fixtures and three Arcadia `.zdl` fixtures |
| Output dir | `ZENWAVE_DEV_VIEWS_DIR` | `zenwave.devViews.dir` | `-PdevViewsDir` | `<repo>/build/dev-views` |

Inputs are a comma-separated list of files or directories (directories are scanned, not recursively, for `*.zfl` and `*.zdl`).
Relative paths resolve against the module directory, then the repo root. Every resolved input and the output directory are printed.

## Output

All files are written atomically (temp file then move), the `.json` files last.

- `<name>.eventflow.json` (ZFL): `{ name, source, generatedAt, flowGraph, serviceGraph, variants: [{id,title}], variantGraphs: {id: FlowViewModel}, laneGraphs: {systems, services} }`.
  View models are serialised as zenwave-lsp does (defaults encoded, nulls omitted).
- `<name>.mermaid.json` (ZFL and ZDL): `{ name, source, generatedAt, diagrams: [{id, title, mermaid, group?}] }`. ZFL: `flowchart` and
  `sequence:<endOutcome>:<index>` (alt blocks mode). ZDL: services, aggregates, state machines, events, commands and other entities, with `group`.
- `<name>/<id>.mmd`: each diagram as plain Mermaid for a VS Code Mermaid preview (characters unsafe in a file name become `_`).
- `<name>.error.txt`: the stack trace when an input fails; removed on the next success. Other inputs are still exported and the test fails at the end.
