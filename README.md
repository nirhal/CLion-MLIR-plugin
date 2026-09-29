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
- Run folders recursively or just their immediate files. Files run sequentially, and failures do not stop other files.

### Running MLIR tests

Click a `RUN:` gutter icon, right-click an MLIR file or folder and choose Run, or select a file/folder in an **MLIR Tests** configuration. **Include subfolders** defaults to enabled. Folder discovery runs in a cancellable background step, skips IDE-excluded paths and directory symlinks, and includes only `.mlir` files with `RUN:` comments. No `lit.cfg.py` is required. An empty selection produces an error.

The first command of each directive must name a CMake executable target. **Build MLIR test tools** resolves all required targets using the selected CLion CMake profile and builds each distinct target once before running any test. Downstream commands that name CMake targets are also built and run from their resolved executable paths; other downstream tools must be available on PATH. A target missing from the selected profile is reported as an error instead of using a different profile. A failed or cancelled build prevents test execution.

Supported syntax includes `%s` (the current file, including paths with spaces), `%%` (a literal percent), quoted/escaped arguments, pipelines, multiple directives, and backslash continuations onto the next `// RUN:` line. Files are executed directly without a shell. Unsupported substitutions, shell operators/expansions, and lit conditional directives produce a diagnostic rather than an incorrect test result.

```mlir
// RUN: mlir-opt %s --canonicalize \
// RUN:   | FileCheck %s --check-prefix=CANONICAL
// RUN: mlir-opt %s --verify-each
```

Each file executes in its own parent directory. The test window groups a folder's results under one suite and labels files with their relative paths, so identical filenames in different subfolders remain distinct. Invalid directives and missing CMake tools appear as failed file tests; other files continue. **Rerun Failed Tests** reruns only the failed files, using their current contents. Saved single-file configurations remain compatible.

Stop cancels the active pipeline and marks remaining files as skipped. Native Debug runs the first directive of a single file and keeps CLion's debugger console. Folder Debug reports that a single file must be selected.

### Verification

Run `./gradlew test buildPlugin` for discovery, execution, CMake planning, persistence, and test-console integration tests. For an IDE smoke test, select a CMake profile and run a folder containing nested tests that use different targets (including a downstream CMake tool), then check the build output, file navigation, Stop, and Rerun Failed Tests. Debug a single file to check the native debugger integration.

## Installation
1. Download from [Releases](https://github.com/nirhal/CLion-MLIR-plugin/releases)
2. In IntelliJ: `File → Settings → Plugins → Install Plugin from Disk`
3. Restart IDE

## Supported IDEs
- IntelliJ IDEA, CLion, and other JetBrains IDEs with language support

## Contributing
Pull requests and issues are welcome.
