# What's Changed

- breaking: replace `Json5.tokenize` with tolerant `Json5.scanSyntax` highlighting spans that distinguish property names and literals and omit whitespace by default
- perf: add synchronous streaming spans and compact preloaded results backed by integer arrays
