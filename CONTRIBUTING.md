# Contributing to MatrixSpigotBridge v2

Thank you for your interest in contributing to **MatrixSpigotBridge v2**! Contributions from the community help keep this project active, reliable, and feature-rich.

Please take a moment to review this guide before opening issues or submitting pull requests.

---

## Prerequisites

To build and contribute to MatrixSpigotBridge, you will need:

- **Java Development Kit (JDK)**: JDK 8 or higher. (The project will ultimately be built with JDK 8 for maximum backwards compatibility).
- **Apache Maven**
- A local **Minecraft / Spigot Server** (Spigot 1.13+) for testing. Testing on Spigot will ensure wide compatibility.

### Setting Up the Environment

1. **Fork & Clone the Repository**

   ```bash
   git clone https://github.com/YOUR-USERNAME/matrix-spigot-bridge.git
   cd matrix-spigot-bridge
   ```

2. **Build the Project**
   Run Maven to download dependencies and assemble the shaded plugin JAR:

   ```bash
   mvn install
   ```

3. **Locate the Compiled Plugin**
   The final compiled plugin file will be produced at:
   ```
   target/MatrixSpigotBridge.jar
   ```
   Copy this file into your server's `plugins/` directory to test.

---

## Reporting Bugs

If you encounter a bug, please check existing GitHub issues to ensure it hasn't already been reported.

When opening a bug report, include:

- **Clear Description**: What failed and what was expected to happen.
- **Steps to Reproduce**: Clear step-by-step instructions.
- **Environment Details**:
  - Plugin version (e.g., `v2.5.0`)
  - Server Software & Version (e.g., Paper 1.20.4, Spigot 1.12.2)
  - Java version
- **Server Logs & Stack Traces**: Paste relevant log snippets (be sure to remove sensitive Matrix access tokens or passwords!).

---

## Development Guidelines

### Code Style & Architecture

- Follow standard Java coding conventions.
- Maintain existing package organization:
  - `com.pseudosmp.msb`: Plugin entry point (`MatrixSpigotBridge`), command handlers, and event listeners.
  - `com.pseudosmp.tools`: Matrix API bridge clients, formatting engines, and game utility functions.
- **Non-blocking Operations**: Matrix API network requests must remain asynchronous to prevent stalling the main Minecraft server tick loop.
- **Dependencies**: Shaded dependencies (`org.json`, `commons-text`, `bstats`) are automatically relocated via `maven-shade-plugin` in `pom.xml`. Avoid adding unneeded heavy external libraries.
- **PlaceholderAPI**: Preserve soft-dependency handling so the plugin functions with or without PlaceholderAPI installed.

### Branching & Commits

- Create a dedicated branch for your feature or bug fix:
  ```bash
  git checkout -b fix/async-connection-timeout
  # or
  git checkout -b feature/custom-formatting-tag
  ```
- Write clear, concise commit messages.

---

## Submitting Pull Requests

1. **Verify your build**: Build with `mvn clean package` and test in-game as well as in your own Matrix room.
2. **Push to your fork**:
   ```bash
   git push origin fix/async-connection-timeout
   ```
3. **Open a Pull Request**:
   - Provide a clear summary of your changes.
   - Reference any relevant GitHub Issue if applicable (e.g., `Fixes #12`).
   - If no issue is available, include the bug report format in your pull request before the description of the solution.
   - Describe the manual testing performed to verify the changes.
4. **Review**: Maintainer(s) will review your PR and provide feedback if any adjustments are needed.

---

Thank you for helping make MatrixSpigotBridge better!
