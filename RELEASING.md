# Releasing

This repository publishes Kotlin Multiplatform artifacts to Maven Central and
(optionally) the generated JS package to npm, through the `Release from Notes`
workflow. The complete security model — trust boundaries, required GitHub and
npm configuration, credential handling, residual risks — is documented in
[docs/release-security.md](docs/release-security.md). Read that before
changing anything under `.github/workflows/`.

## Artifacts

- Maven Central: `io.zenwave360.dsl:dsl-kotlin`, `-jvm`, `-js`
- npm (optional): `@zenwave360/dsl`

## Release steps

1. Write `release-notes/release-notes.v<VERSION>.md` (plain feature list, not
   a commit log; it must mention the version) and commit it — along with any
   intended release changes — to `main`.
2. Run the **Release from Notes** workflow from the `main` branch with:
   - `version`, e.g. `1.0.0` (or `1.0.0-rc.1`); no `v` prefix
   - `developmentVersion`, optional; defaults to the next patch `-SNAPSHOT`
   - `publishNpm`, default `false` until the npm account is set up
3. The workflow validates everything, creates the release commit and tag
   `v<VERSION>` on `main`, builds and tests (JVM, JS, Node integration tests)
   without any credentials, then waits.
4. Approve the **maven-central-upload** environment. The privileged job signs
   the verified artifacts and uploads the deployment as **USER_MANAGED**
   (`autoPublish=false`), then creates the GitHub release and syncs `main`
   back to `develop`.
5. Go to [central.sonatype.com](https://central.sonatype.com/publishing/deployments),
   review the deployment, and click **Publish**. Nothing is public until you do.
6. If `publishNpm` was checked: approve the **npm-publish** environment
   (typically after the Portal Publish click). The verified tarball is
   published via OIDC trusted publishing with provenance — no npm token exists
   anywhere.

The first-ever npm publish is manual (OIDC trusted publishing requires an
existing package): see
[docs/release-security.md#first-npm-publish](docs/release-security.md#first-npm-publish).

## Snapshot publishing

The `Build and Publish Snapshots` workflow runs
`./gradlew publishAllPublicationsToMavenCentralRepository` on every push to
`develop`/`next`. Keep the version on a `-SNAPSHOT` suffix between releases —
the workflow refuses to publish otherwise.

## Required configuration (one-time)

GitHub Environments `maven-central-upload` (reviewer required, `main` only,
holds `CENTRAL_USERNAME`/`CENTRAL_TOKEN`/`SIGN_KEY`/`SIGN_KEY_PASS`),
`maven-central-snapshots` (`develop`/`next`, same secrets), and `npm-publish`
(reviewer required, `main` only, no secrets); `main` and `v*` rulesets; and
read-only default workflow permissions. Details and rationale:
[docs/release-security.md](docs/release-security.md#required-github-configuration).

## What changed from the previous flow

The old `Create Gradle Release` (PR + auto-merge) and
`Publish Release to Maven Central and NPM` (triggered on `release: created`,
accepting an arbitrary `tag` dispatch input) workflows are gone. They ran the
full build — including signing — in one ungated job/step. See
[docs/release-security.md](docs/release-security.md) for why the new model
replaces them.
