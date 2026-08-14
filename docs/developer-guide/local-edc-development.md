# Local EDC Connector Development

This guide explains how to compile the [EDC Connector](https://github.com/eclipse-edc/Connector) repository locally and link it to the Dataspace Ecosystem project. This is useful when you need to:

- Test a fix or feature in the Connector before it's released upstream
- Develop against a SNAPSHOT version of the Connector
- Debug Connector behavior with local changes

---

## Prerequisites

- Java 17+ (same JDK used for the Dataspace Ecosystem project)
- Git
- Gradle (the wrapper is included in both projects)

---

## Step 1: Clone the EDC Connector

```bash
git clone https://github.com/eclipse-edc/Connector.git
cd Connector
```

---

## Step 2: Check out the target branch

Check out the branch that contains the changes you need. For example, to use a bugfix branch:

```bash
git checkout upstream/bugfix/0.16.1
# Or create a local branch from it:
git checkout -b my-local-changes upstream/bugfix/0.16.1
```

---

## Step 3: Verify the Connector version

Check what version the branch will publish:

```bash
cat gradle.properties | grep version
```

Example output:

```
version=0.16.1-SNAPSHOT
```

Take note of this version — you will need it in Step 5.

---

## Step 4: Publish EDC to your local Maven repository

Run the following command to build and publish all EDC artifacts to `~/.m2/repository`:

```bash
./gradlew publishToMavenLocal -Pskip.signing=true -x test -x check
```

| Flag | Purpose |
|------|---------|
| `-Pskip.signing=true` | Skip GPG signing (not needed for local use) |
| `-x test` | Skip tests to speed up the build |
| `-x check` | Skip static analysis and checkstyle |

This takes approximately 2 minutes. When complete, artifacts will be available at:

```
~/.m2/repository/org/eclipse/edc/
```

---

## Step 5: Configure the Dataspace Ecosystem to use the local build

### 5.1 Update the EDC version

Edit `gradle/libs.versions.toml` and change the `edc` version to match the one from Step 3:

```toml
[versions]
edc = "0.16.1-SNAPSHOT"    # Changed from "0.16.0"
```

### 5.2 Handle non-Connector EDC artifacts

The Dataspace Ecosystem also depends on artifacts from other EDC repositories (IdentityHub, FederatedCatalog, IssuerService) that share the same version reference. Since these are **not** part of the Connector repo, they won't be in your local Maven repository.

You need to split their versions so they remain pinned to the released version:

```toml
[versions]
edc = "0.16.1-SNAPSHOT"    # Connector artifacts (from mavenLocal)
edc-ih = "0.16.0"          # IdentityHub artifacts (from Maven Central)
edc-fc = "0.16.0"          # FederatedCatalog artifacts (from Maven Central)
edc-is = "0.16.0"          # IssuerService artifacts (from Maven Central)
```

Then update the corresponding library declarations to use their respective version refs:

```toml
# IdentityHub artifacts → use edc-ih
edc-identityhub-spi-core = { module = "org.eclipse.edc:identity-hub-spi", version.ref = "edc-ih" }
edc-identityhub-bom = { module = "org.eclipse.edc:identityhub-bom", version.ref = "edc-ih" }
# ... (all edc-identityhub-* entries)

# FederatedCatalog artifacts → use edc-fc
edc-federatedcatalog-spi-core = { module = "org.eclipse.edc:federated-catalog-spi", version.ref = "edc-fc" }
edc-federatedcatalog-bom = { module = "org.eclipse.edc:federatedcatalog-dcp-bom", version.ref = "edc-fc" }
# ... (all edc-federatedcatalog-* entries)

# IssuerService artifacts → use edc-is
edc-issuerservice-bom = { module = "org.eclipse.edc:issuerservice-bom", version.ref = "edc-is" }
# ... (all edc-issuerservice-* entries)
```

### 5.3 Verify mavenLocal is configured

The project's `settings.gradle.kts` has `mavenLocal()` commented out by default to ensure reproducible builds from published artifacts. To use local EDC artifacts, **uncomment** it in both the `pluginManagement` and `dependencyResolutionManagement` repository blocks:

```kotlin
pluginManagement {
    repositories {
        mavenLocal() // Uncomment this line to use locally compiled EDC
        gradlePluginPortal()
        mavenCentral()
        // ...
    }
}
dependencyResolutionManagement {
    repositories {
        mavenLocal() // Uncomment this line to use locally compiled EDC
        gradlePluginPortal()
        mavenCentral()
        // ...
    }
}
```

Ensure `mavenLocal()` is the **first** repository so local artifacts take priority over remote ones.

---

## Step 6: Verify the integration

Compile the Dataspace Ecosystem project to confirm it resolves your local EDC artifacts:

```bash
./gradlew compileJava
```

To verify the correct version is being used:

```bash
./gradlew :core:controlplane:dependencies --configuration compileClasspath | grep "org.eclipse.edc"
```

You should see `0.16.1-SNAPSHOT` for Connector artifacts.

---

## Iterating on changes

When you make further changes to the EDC Connector, re-publish to mavenLocal:

```bash
cd /path/to/Connector
./gradlew publishToMavenLocal -Pskip.signing=true -x test -x check
```

Then in the Dataspace Ecosystem project, refresh dependencies:

```bash
cd /path/to/Dataspace_Ecosystem
./gradlew --refresh-dependencies compileJava
```

---

## Troubleshooting

### `Could not find org.eclipse.edc:<artifact>:<version>`

- Verify the artifact is in `~/.m2/repository/org/eclipse/edc/<artifact>/<version>/`
- If it's an IdentityHub/FederatedCatalog/IssuerService artifact, it must remain on the released version (see Step 5.2)
- Check that `mavenLocal()` is listed in the repository configuration

### Port binding errors in tests

When running integration tests with `RuntimePerClassExtension`, use `getFreePort()` for all web endpoints:

```java
config.put("web.http.port", String.valueOf(getFreePort()));
config.put("web.http.management.port", String.valueOf(getFreePort()));
config.put("web.http.protocol.port", String.valueOf(getFreePort()));
```

### GPG signing error during `publishToMavenLocal`

Ensure you pass `-Pskip.signing=true` to the Gradle command.

### Tests not running (`0 tests`)

The EDC build plugin excludes tests tagged with `@IntegrationTest` (and `@EndToEndTest`) by default. Run with:

```bash
./gradlew test -DrunAllTests=true
```

---

## Related

- [EDC Connector Repository](https://github.com/eclipse-edc/Connector)
- [EDC Developer Documentation](https://eclipse-edc.github.io)
- [Project Structure](project-structure.md)
- [Testing Guide](testing.md)
