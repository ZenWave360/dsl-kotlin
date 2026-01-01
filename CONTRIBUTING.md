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
```

## Release Process

### 1. Create a Release

Trigger the **Create Gradle Release** workflow from GitHub Actions:

1. Go to **Actions** → **Create Gradle Release** → **Run workflow**
2. Enter the release version (e.g., `1.5.0`)
3. Enter the next development version (e.g., `1.6.0-SNAPSHOT`)

This workflow will:
- Update version in `build.gradle.kts` to the release version
- Create a git tag `v{version}`
- Update version to the next development version
- Create a PR with the changes
- Auto-merge the PR
- Push the release tag

### 2. Publish the Release

After the tag is created, create a GitHub Release:

1. Go to **Releases** → **Draft a new release**
2. Select the tag created in step 1 (e.g., `v1.5.0`)
3. Generate release notes or write your own
4. Publish the release

This automatically triggers the **Publish Release to Maven Central and NPM** workflow, which:
- Builds and tests the project
- Publishes to Maven Central
- Publishes to NPM registry as `@zenwave360/zdl`

### 3. Snapshot Releases

Snapshots are automatically published when pushing to `develop` or `next` branches via the **Build and Publish Snapshots** workflow.

> **Note**: Snapshot publishing to Maven Central is currently disabled. Enable by uncommenting the publish step in `.github/workflows/publish-snapshots.yml`.

## Required Secrets

The following GitHub secrets must be configured for releases:

- `CENTRAL_USERNAME` - Maven Central username
- `CENTRAL_TOKEN` - Maven Central token
- `SIGN_KEY` - GPG signing key
- `SIGN_KEY_PASS` - GPG signing key password
- `NPM_TOKEN` - NPM authentication token

