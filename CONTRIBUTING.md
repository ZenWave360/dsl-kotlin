# Contributing to ZDL Kotlin

## Building Locally

### Prerequisites
- JDK 17 or higher
- Node.js 18 or higher (for JS/NPM package)

### Build Commands

```bash
# Build the project
./gradlew clean build

# Run tests
./gradlew jvmTest

# Run tests with coverage
./gradlew build koverHtmlReport

# Build JS package and run Node.js integration tests
./gradlew nodeIntegrationTest

# Publish to local Maven repository
./gradlew clean publishToMavenLocal

# Build and publish to Maven repository
./gradlew clean build publishToMavenLocal
```

## Release Process

See [RELEASING.md](RELEASING.md) and [docs/release-security.md](docs/release-security.md)
for the full flow. In short: add a `release-notes/release-notes.v<version>.md`
file to `main`, then trigger the **Release from Notes** workflow from GitHub
Actions with the release version (and, optionally, the next development
version and whether to publish to npm). It prepares the version bump, tags the
release, builds credential-free, and — after you approve the protected
`maven-central-upload` environment — signs and uploads the deployment to Maven
Central as USER_MANAGED, then creates the GitHub Release. A human still has to
log into [central.sonatype.com](https://central.sonatype.com) and click
**Publish** to make it live.

### Snapshot Releases

Snapshots are automatically published when pushing to `develop` or `next` branches via the **Build and Publish Snapshots** workflow (`.github/workflows/publish-maven-snapshots.yml`).

## Required Secrets

The following GitHub secrets must be configured, scoped to the `maven-central-upload` and `maven-central-snapshots` GitHub Environments (not repository-level — see [docs/release-security.md](docs/release-security.md)):

- `CENTRAL_USERNAME` - Maven Central username
- `CENTRAL_TOKEN` - Maven Central token
- `SIGN_KEY` - GPG signing key
- `SIGN_KEY_PASS` - GPG signing key password

npm publication uses OIDC trusted publishing — no npm token is ever stored.

