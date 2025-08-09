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

## Installation
1. Download from [Releases](https://github.com/nirhal/CLion-MLIR-plugin/releases)
2. In IntelliJ: `File → Settings → Plugins → Install Plugin from Disk`
3. Restart IDE

## Supported IDEs
- IntelliJ IDEA, CLion, and other JetBrains IDEs with language support

## Contributing
Pull requests and issues are welcome.