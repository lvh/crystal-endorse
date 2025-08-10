# crystal-endorse

A shim to make OpenSSH-based signing work like GPG clearsign. (Clear sign. Crystal endorse. Get it?)

This is useful for existing legacy software that expects to shell out to `gpg --clearsign` and you'd like it to use SSH signatures (which are much better) instead. It will also let you extract the signature again for validation.

This was originally intended for [Fossil SCM][fossil]. For documentation purposes, see:

* [a signed artifact (10fb90fcae08a311)][signed]
* [an unsigned one (cb13b611005b9f0a)][unsigned]

[fossil]: https://fossil-scm.org/
[signed]: https://fossil-scm.org/home/artifact/10fb90fcae08a311
[unsigned]: https://fossil-scm.org/home/artifact/cb13b611005b9f0a

## Features

- Makes SSH signing compatible with GPG clearsign format
- Enables legacy applications that expect GPG to work with SSH signatures
- Designed to work well with Fossil SCM
- Written in Clojure

## Installation

### Prerequisites

- JDK 8 or newer
- [Clojure CLI tools](https://clojure.org/guides/install_clojure)

### Building from Source

Clone this repository and build the project:

```bash
git clone https://github.com/lvh/crystal-endorse.git
cd crystal-endorse
clojure -T:build ci
```

The uberjar will be created in the `target` directory.

## Usage

```bash
# Basic usage
java -jar target/crystal-endorse-0.1.0-SNAPSHOT.jar [options] [arguments]
```

This project is intended to be used with Fossil SCM.

## Known Limitations

We don't handle [dash escaping][dash-escape] at all right now. This is OK for our limited intended use case because the [Fossil manifest file format][fsl-manifest] strictly consists of lines starting with an ASCII uppercase letter followed by a space. While technically an implementation MAY escape any line, de facto only lines starting with a dash are escaped. Fossil does not implement this escaping at all, presumably relying that the implementation would not escape lines unnecessarily, even if the specification allows it.

[dash-escape]: https://datatracker.ietf.org/doc/html/rfc4880#section-7.1
[fsl-manifest]: https://fossil-scm.org/home/doc/tip/www/fileformat.wiki

## Development

### Running Tests

```bash
clojure -X:test :run
```

### Building

```bash
# Build the project and create an uberjar
clojure -T:build ci

# Run only the tests
clojure -T:build test
```

## License

Copyright © Laurens Van Houtven

Distributed under the Eclipse Public License version 1.0.