# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Crystal-endorse is a Clojure-based shim that makes OpenSSH-based signing work like GPG clearsign. It's designed to be used with existing legacy software that expects to shell out to `gpg --clearsign` but would benefit from using SSH signatures instead. The project was originally intended for use with Fossil SCM.

## Common Commands

### Building the Project

```bash
# Build the project and create an uberjar
clojure -T:build ci

# Run only the tests
clojure -T:build test
```

### Testing

```bash
# Run all tests
clojure -X:test :run
```

## Code Architecture

This is a Clojure project with a simple structure:

- **src/io/lvh/crystal_endorse.clj**: Main source file containing functions for:
  - Splitting and handling clearsigned messages
  - Working with the Fossil user card format
  - Constants for PGP message formatting

The project uses several libraries:
- **babashka/process**: For shelling out to external commands
- **babashka/fs**: For filesystem operations
- **meander/epsilon**: For declarative data transformation (used in the split-clearsign function)
- **clojure/tools.cli**: For command-line argument parsing

The main functionality revolves around making SSH signatures compatible with systems expecting GPG clearsign format, particularly for the Fossil SCM.

Note: As of the latest version, some functions like `sign` and `-main` are defined but not yet implemented.