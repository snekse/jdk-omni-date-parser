# Implementation Spec: jdk-omni-date-parser - Phase 4

**Contract**: ./contract.md
**Estimated Effort**: M

## Technical Approach

Phase 4 completes the Maven Central publishing pipeline. Phase 3 scaffolded the `publishing` block and POM metadata in `build.gradle.kts`; this phase wires up GPG signing, the Central Portal deployment API, CI automation via GitHub Actions, and versioning.

### Publishing Pathway: Central Portal (not legacy OSSRH)

Sonatype's legacy OSSRH (OSS Repository Hosting) required a JIRA ticket to claim a namespace and published via the Nexus staging workflow. As of 2024, new projects must use the **Central Portal** at https://central.sonatype.com. Key differences:

| Aspect | Legacy OSSRH | Central Portal |
|--------|-------------|----------------|
| Namespace claim | JIRA ticket → manual review | Automatic for `io.github.*` via GitHub login |
| Staging API | Nexus staging plugin | REST API (`/api/v1/publisher/upload`) |
| Gradle support | `maven-publish` + Nexus staging plugin | No official plugin; community plugins or manual bundle upload |
| Signing | GPG required | GPG required (no change) |

**Decision**: Use the Central Portal with the community plugin [`central-publishing`](https://github.com/GradleUp/nmcp) from GradleUp (formerly `nmcp`), which is the most widely adopted community solution. If the official Sonatype Gradle plugin ships before we publish, switch to it.

### Namespace

`io.github.snekse` — automatically verified because the GitHub account `snekse` registered on the Central Portal. No DNS TXT records or verification repos needed.

## Feedback Strategy

**Inner-loop command**: `./gradlew publishToMavenLocal` (validates POM, signing, sources/javadoc jars locally without uploading)

**Playground**: Local Maven repo (`~/.m2/repository/io/github/snekse/jdk-omni-date-parser/`) — inspect published artifacts, POM, signatures.

**Why this approach**: Publishing to Central is irreversible (released versions cannot be deleted). Local publishing catches POM and signing issues safely. The actual Central Portal upload is done only via CI or a deliberate manual step.

## Prerequisites

Before implementation begins:

1. **Central Portal account**: Register at https://central.sonatype.com with GitHub OAuth (grants `io.github.snekse` namespace automatically)
2. **Central Portal user token**: Generate from Account → User Token page. Yields a `username` and `password` pair used for API auth.
3. **GPG key**: Generate a signing key and publish the public key to a keyserver (`keys.openpgp.org` or `keyserver.ubuntu.com`)
4. **GitHub repo secrets**: Add the following to the repo's Settings → Secrets and variables → Actions:
   - `MAVEN_CENTRAL_USERNAME` — Central Portal token username
   - `MAVEN_CENTRAL_PASSWORD` — Central Portal token password
   - `GPG_SIGNING_KEY` — ASCII-armored private key (`gpg --armor --export-secret-keys <keyId>`)
   - `GPG_SIGNING_PASSWORD` — passphrase for the GPG key

## File Changes

### New Files

| File Path | Purpose |
|-----------|---------|
| `.github/workflows/ci.yml` | CI: build + test on every push/PR |
| `.github/workflows/release.yml` | Release: build, sign, publish to Central Portal on version tag push |

### Modified Files

| File Path | Changes |
|-----------|---------|
| `build.gradle.kts` | Add `central-publishing` plugin, GPG signing config, remove version `-SNAPSHOT` suffix for releases |
| `gradle.properties` | Add signing and Central Portal property placeholders (values come from env vars / secrets) |

## Implementation Details

### GPG Signing

**Overview**: Maven Central requires all artifacts to have `.asc` GPG signature files. Gradle's built-in `signing` plugin handles this.

```kotlin
// build.gradle.kts
signing {
    // Use in-memory key from environment variables (CI-friendly)
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["mavenJava"])
}

// Only sign when publishing (not on every build)
tasks.withType<Sign>().configureEach {
    onlyIf { project.hasProperty("signingKey") }
}
```

**Implementation steps**:
1. Uncomment the `signing` block in `build.gradle.kts`
2. Switch to `useInMemoryPgpKeys()` for CI compatibility (avoids needing a GPG keyring on the build machine)
3. Guard signing with `onlyIf` so local `./gradlew build` doesn't fail without a key
4. Test: `./gradlew publishToMavenLocal -PsigningKey="$(gpg --armor --export-secret-keys <keyId>)" -PsigningPassword="<passphrase>"` — verify `.asc` files appear in `~/.m2/`

### Central Portal Plugin

**Overview**: The `central-publishing` Gradle plugin from GradleUp handles bundling artifacts into the zip format the Central Portal API expects, uploading, and waiting for validation.

```kotlin
// build.gradle.kts
plugins {
    // ... existing plugins ...
    id("com.gradleup.nmcp") version "0.1.4"
}

nmcp {
    publishAllProjectsProbablyBreakingProjectIsolation {
        username = project.findProperty("centralUsername") as String? ?: ""
        password = project.findProperty("centralPassword") as String? ?: ""
        publicationType = "USER_MANAGED"  // require manual "Publish" click on first release; switch to AUTOMATIC later
    }
}
```

**Key decisions**:
- `USER_MANAGED` for the first release — this means the bundle uploads and validates, but waits for manual confirmation on the Central Portal web UI before going live. Switch to `AUTOMATIC` once the workflow is proven.
- Credentials come from Gradle properties (injected by CI from secrets, or passed via `-P` flags locally).

**Implementation steps**:
1. Add the `com.gradleup.nmcp` plugin to the plugins block
2. Add the `nmcp` configuration block
3. Add `centralUsername` and `centralPassword` to `gradle.properties` with empty defaults
4. Test locally: `./gradlew publishToMavenLocal` should still work without Central credentials

### Versioning

**Overview**: The project currently uses `0.1.0-SNAPSHOT`. Maven Central does not accept `-SNAPSHOT` versions. The release workflow will set the version from the git tag.

```kotlin
// build.gradle.kts
version = findProperty("releaseVersion")?.toString() ?: "0.1.0-SNAPSHOT"
```

**Workflow**:
- Development builds: `0.1.0-SNAPSHOT` (default)
- Release: push a git tag like `v0.1.0` → CI extracts `0.1.0` and passes `-PreleaseVersion=0.1.0`
- Post-release: bump `SNAPSHOT` version in `build.gradle.kts` for next development cycle

**Implementation steps**:
1. Change the `version` line in `build.gradle.kts` to read from `releaseVersion` property with SNAPSHOT fallback
2. Verify `./gradlew build` still uses SNAPSHOT by default
3. Verify `./gradlew build -PreleaseVersion=0.1.0` produces non-SNAPSHOT artifacts

### POM Completeness

**Overview**: Maven Central requires specific POM metadata. Phase 3 scaffolded most of it, but the `developers` block needs a `name` and the `scm` block needs a `developerConnection`.

```kotlin
// build.gradle.kts — additions to the existing pom block
developers {
    developer {
        id = "snekse"
        name = "Derek Eskens"
    }
}
scm {
    connection = "scm:git:git://github.com/snekse/jdk-omni-date-parser.git"
    developerConnection = "scm:git:ssh://github.com:snekse/jdk-omni-date-parser.git"
    url = "https://github.com/snekse/jdk-omni-date-parser"
}
```

**Implementation steps**:
1. Add `name` to the `developer` block
2. Add `developerConnection` to the `scm` block
3. Verify: `./gradlew generatePomFileForMavenJavaPublication` → inspect `build/publications/mavenJava/pom-default.xml` for completeness

### CI Workflow (Build + Test)

**Overview**: Runs on every push and PR. Validates the project builds and all tests pass on JDK 21.

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew build
```

### Release Workflow (Publish to Central)

**Overview**: Triggered by pushing a version tag (`v*`). Builds, signs, and uploads to the Central Portal.

```yaml
# .github/workflows/release.yml
name: Release

on:
  push:
    tags: ['v*']

jobs:
  publish:
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4

      - name: Extract version from tag
        id: version
        run: echo "version=${GITHUB_REF_NAME#v}" >> "$GITHUB_OUTPUT"

      - name: Publish to Maven Central
        run: ./gradlew publishAllPublicationsToCentralPortal
        env:
          ORG_GRADLE_PROJECT_releaseVersion: ${{ steps.version.outputs.version }}
          ORG_GRADLE_PROJECT_signingKey: ${{ secrets.GPG_SIGNING_KEY }}
          ORG_GRADLE_PROJECT_signingPassword: ${{ secrets.GPG_SIGNING_PASSWORD }}
          ORG_GRADLE_PROJECT_centralUsername: ${{ secrets.MAVEN_CENTRAL_USERNAME }}
          ORG_GRADLE_PROJECT_centralPassword: ${{ secrets.MAVEN_CENTRAL_PASSWORD }}
```

**Key decisions**:
- Tag-triggered: only `v*` tags trigger a release, not every push to main
- Version extracted from tag name: `v0.1.0` → `0.1.0`
- Secrets injected as `ORG_GRADLE_PROJECT_*` environment variables — Gradle reads these as project properties automatically
- First release uses `USER_MANAGED` — after the tag push, go to https://central.sonatype.com, verify the deployment looks correct, then click "Publish"

## Release Checklist (First Release)

This is the one-time setup checklist for the first publish. Include as a comment in `release.yml` or a `RELEASING.md` file:

1. [ ] Register at https://central.sonatype.com with GitHub account (auto-verifies `io.github.snekse`)
2. [ ] Generate user token (Account → Generate User Token)
3. [ ] Generate GPG key: `gpg --full-generate-key` (RSA 4096, no expiry or long expiry)
4. [ ] Publish GPG public key: `gpg --keyserver keys.openpgp.org --send-keys <keyId>`
5. [ ] Add GitHub repo secrets: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `GPG_SIGNING_KEY`, `GPG_SIGNING_PASSWORD`
6. [ ] Create and push tag: `git tag v0.1.0 && git push origin v0.1.0`
7. [ ] Monitor GitHub Actions for the Release workflow
8. [ ] Go to Central Portal → verify deployment → click "Publish"
9. [ ] Wait ~30 minutes for artifacts to appear on https://repo1.maven.org/maven2/io/github/snekse/jdk-omni-date-parser/
10. [ ] After first successful release, consider switching `publicationType` to `AUTOMATIC`

## Testing Requirements

### Local Publishing Validation

- [ ] `./gradlew publishToMavenLocal` succeeds and produces jars + POM under `~/.m2/repository/io/github/snekse/jdk-omni-date-parser/0.1.0-SNAPSHOT/`
- [ ] POM contains: groupId, artifactId, version, name, description, url, license, developer (with name), scm (with developerConnection)
- [ ] `-sources.jar` and `-javadoc.jar` are present alongside the main jar
- [ ] With signing key provided, `.asc` files are generated for all artifacts

### CI Validation

- [ ] `ci.yml` triggers on push to main and on PRs
- [ ] Build passes on JDK 21 (Ubuntu)

### Release Validation (dry run)

- [ ] `./gradlew publishToMavenLocal -PreleaseVersion=0.1.0` produces artifacts with version `0.1.0` (no SNAPSHOT)
- [ ] `./gradlew publishToMavenLocal -PreleaseVersion=0.1.0 -PsigningKey="..." -PsigningPassword="..."` produces signed artifacts

## Validation Commands

```bash
# Full test suite (must still pass)
./gradlew test

# Local publish (unsigned)
./gradlew publishToMavenLocal

# Local publish (signed, dry run for release)
./gradlew publishToMavenLocal -PreleaseVersion=0.1.0 \
  -PsigningKey="$(gpg --armor --export-secret-keys <keyId>)" \
  -PsigningPassword="<passphrase>"

# Inspect generated POM
./gradlew generatePomFileForMavenJavaPublication
cat build/publications/mavenJava/pom-default.xml

# Inspect local Maven repo artifacts
ls ~/.m2/repository/io/github/snekse/jdk-omni-date-parser/
```

## Open Items

- [ ] Decide whether to create a `RELEASING.md` file or keep the release checklist as a comment in the workflow
- [ ] Evaluate whether to add a `NOTICE` file (Apache 2.0 convention, not strictly required by Maven Central)
- [ ] After first successful release, switch `publicationType` from `USER_MANAGED` to `AUTOMATIC`
- [ ] Consider adding a GitHub Actions workflow for snapshot publishing (on push to main) if there's demand for pre-release artifacts

---

_This spec is ready for implementation. Prerequisites (Central Portal account, GPG key, GitHub secrets) must be completed before the CI/release workflows can be tested end-to-end._
