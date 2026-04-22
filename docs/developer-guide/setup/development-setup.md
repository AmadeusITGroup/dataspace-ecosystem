# Development Setup

Set up your development environment for contributing to the Dataspace Ecosystem.

## IDE Setup

You can use any IDE that supports Java development. Below we provide setup instructions for **IntelliJ IDEA** and **VS Code** as examples, but feel free to use whichever IDE you are most comfortable with.

### IntelliJ IDEA

1. **Import Project**
   - Open IntelliJ IDEA
   - Select "Open" and choose the project root
   - Wait for Gradle sync to complete

2. **Configure SDK**
   - Go to `File > Project Structure > Project`
   - Set SDK to JDK 17+
   - Set Language Level to 17

### VS Code

1. **Install Extensions**
   - Install the [Java Extension Pack](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack) (includes Language Support for Java, Debugger for Java, and Gradle for Java)
   - Install the [Gradle for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-gradle) extension if not already included

2. **Open Project**
   - Open VS Code
   - Select `File > Open Folder...` and choose the project root
   - Wait for the Java Language Server to initialize and Gradle sync to complete

3. **Configure JDK**
   - Open Settings (`Ctrl+,` / `Cmd+,`)
   - Search for `java.configuration.runtimes`
   - Add an entry pointing to your JDK 17+ installation
   - Alternatively, set `java.jdt.ls.java.home` in `settings.json`:

       ```json
       {
         "java.jdt.ls.java.home": "/path/to/jdk-17"
       }
       ```


## Build System

### Basic Gradle Commands

For basic build commands (full build, building without tests, clean build, etc.), refer to the [Quick Start Guide](../../getting-started/quick-start.md).

### Development-Specific Commands

```bash
# Build specific module
./gradlew :core:common:build

# Run specific launcher
./gradlew :launchers:control-plane:control-plane-launcher:run
```

## Running Locally

### Full Dataspace Deployment

For deploying a complete dataspace environment with multiple participants, see the [Testing Guide](../testing.md) which covers:
- Building Docker/Podman images
- Loading images into Kind clusters
- Deploying with Terraform

### System Tests (End-to-End)

For end-to-end testing with a full dataspace deployment, see [Quick Start - Step 5: Run End-to-End Tests](../../getting-started/quick-start.md#step-5-run-end-to-end-tests).

This includes:
- Setting up port forwarding
- Running system tests: `./gradlew :system-tests:runner:test -DincludeTags="EndToEndTest"`
- Requirements for the deployed dataspace

!!! warning "One-Time Execution"
    End-to-end tests can only run once per deployment. Destroy and redeploy the dataspace to rerun them.

## See Also

- [Project Structure](../project-structure.md) - Understand the repository layout
- [Testing Guide](../testing.md) - System tests and end-to-end testing
