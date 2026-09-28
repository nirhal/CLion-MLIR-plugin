# IntelliJ MLIR Plugin

An IntelliJ IDEA plugin adding MLIR (Multi-Level Intermediate Representation) language support.

## Features

- **Syntax Highlighting** – customizable color schemes
- **File Recognition** – automatic handling of `.mlir` files
- **Lexer & Parser** – full PSI-based analysis, brace matching, and commenting
- **Navigation** – find usages, resolve SSA values and symbols
- **Custom Colors** – configurable in IDE settings

### CLion Extras
- Adds run configs, play icons, and auto-generated execution setups for MLIR `RUN` directives (e.g., `// RUN: mlir-opt %s | filecheck %s`)
- Run a file in the test runner window, with pass/fail status, duration, output, source navigation, and rerun failed tests.
- All `RUN:` directives in a file execute in order as one test. A failed pipeline stage fails the test and stops later directives.

### Running MLIR tests

Click a `RUN:` gutter icon or run an MLIR file configuration. The first command of each directive must name a CMake target; downstream tools such as `FileCheck` must be available on PATH. CLion's existing build step builds the configuration's first target. Build any additional targets used by later directives beforehand.

Supported syntax includes `%s` (the current file, including paths with spaces), `%%` (a literal percent), quoted/escaped arguments, pipelines, multiple directives, and backslash continuations onto the next `// RUN:` line. Files are executed directly without a shell. Unsupported substitutions, shell operators/expansions, and lit conditional directives produce a diagnostic rather than an incorrect test result.

```mlir
// RUN: mlir-opt %s --canonicalize \
// RUN:   | FileCheck %s --check-prefix=CANONICAL
// RUN: mlir-opt %s --verify-each
```

Stop cancels the active pipeline and skips remaining directives. Native Debug runs the first directive only and keeps CLion's debugger console. Folder runs are not implemented yet.

## Installation
1. Download from [Releases](https://github.com/nirhal/CLion-MLIR-plugin/releases)
2. In IntelliJ: `File → Settings → Plugins → Install Plugin from Disk`
3. Restart IDE

## Supported IDEs
- IntelliJ IDEA, CLion, and other JetBrains IDEs with language support

## Contributing
Pull requests and issues are welcome.
