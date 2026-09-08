# kotlin-json5

[![Maven Central](https://img.shields.io/maven-central/v/li.songe/json5.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/li.songe/json5)
[![License](http://img.shields.io/:License-Apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

kotlin multiplatform [json5](https://json5.org) for [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)

complete multiplatform support: jvm/js/native

## Usage

```kotlin
// latest -> https://github.com/lisonge/kotlin-json5/releases
implementation("li.songe:json5:latest")
```

## Decode

Json5String -> JsonElement

```kotlin
// import li.songe.json5.Json5

val element = Json5.parseToJsonElement("{a:1}")
```

Json5String -> Object

```kotlin
// import kotlinx.serialization.json.Json
// import kotlinx.serialization.Serializable
// import li.songe.json5.decodeFromJson5String

val json = Json {
    // add your json config
    ignoreUnknownKeys = true
}

@Serializable
data class A(val id:Int)

val a = json.decodeFromJson5String<A>("{id:0, b:''}")
```

### Parse JSON5 syntax

JSON5 text, including incomplete input, to a source-aware document

```kotlin
val document = Json5.parseToDocument("{a:1,]{")
val tokens = document.tokens
val diagnostics = document.diagnostics
```

For fast, tolerant syntax highlighting, scan the input without building an AST,
decoding values, or producing diagnostics:

```kotlin
Json5.scanSyntax(source) { kind, start, end ->
    highlight(start, end, kind)
}
```

The callback overload emits each span synchronously without retaining a token
result. Whitespace is scanned for recovery but omitted by default; pass
`ignoreWhitespace = false` when a complete token tape is needed. To scan ahead
and cache theme-independent spans, use the materialized overload:

```kotlin
val syntax = Json5.scanSyntax(source)
for (index in syntax.indices) {
    highlight(
        syntax.startAt(index),
        syntax.endAt(index),
        syntax.kindAt(index),
    )
}
```

`scanSyntax` distinguishes property names, null and boolean literals, numbers,
strings, comments, and invalid fragments. It accepts incomplete or syntactically
invalid input. Materialized results are compact and read-only, backed by one
integer array.

### Lossless AST

Parse JSON5 into a source-aware document. The document keeps property order,
duplicate properties, comments, tokens, source ranges, and recoverable diagnostics.

```kotlin
val document = Json5.parseToDocument(
    """
    {
      // server port
      port: 8080,
    }
    """.trimIndent(),
)

val root = document.root
val comments = document.comments
val element = document.toJsonElement()
val port = document[Json5Path["port"]]
```

`parseToDocument` recovers incomplete JSON5 and reports syntax diagnostics.
Formatting, semantic conversion, and structural editing require a valid document.
Duplicate object properties stay distinct in the AST;
conversion to `JsonElement` keeps the last value for compatibility.

## Encode

JsonElement -> Json5String

```kotlin
// import li.songe.json5.Json5

val formatted: String = Json5.encodeToString(element)
```

Encode an individual string literal or property name without wrapping it in a
`JsonElement` or `JsonObject`:

```kotlin
val config = Json5EncoderConfig(quoteStrategy = Json5QuoteStrategy.PreferSingle)
val stringLiteral = Json5.encodeString("it's JSON5", config)
val propertyName = Json5.encodeKey("server-port")
```

Object -> Json5String

```kotlin
// import kotlinx.serialization.json.Json
// import kotlinx.serialization.Serializable
// import li.songe.json5.encodeToJson5String

val json = Json {
  // add your json config
}

@Serializable
data class A(val id:Int)

val formatted: String = json.encodeToJson5String(A(id=0))
```

or use [Json5EncoderConfig](json5/src/commonMain/kotlin/li/songe/json5/Json5EncoderConfig.kt)

## Format

Formatting preserves comments and raw literals such as quote style and
hexadecimal numbers by default.

```kotlin
val formatted = Json5.format(
    "{port:0x1f90,// default\nenabled:true,}",
)
```

Use `Json5FormatConfig` to configure indentation, line separators, trailing
commas, the final newline, and optional string and property-name re-encoding.
Set `quoteStrategy` to normalize quotes and `unquotedKey` to control whether
identifier-compatible property names remain unquoted. Re-encoding may normalize
escape sequences as well as quote characters. Editor integrations can call
`Json5.formatToEdits` and apply the result with `source.applyJson5Edits(edits)`.

## Edit

Source-aware edits only replace the selected ranges and preserve unrelated
comments and formatting.

```kotlin
val path = Json5Path["server"]["port"]
val result = Json5.parseToDocument(source).set(path, JsonPrimitive(8080))

val editedText = result.text
val updatedDocument = result.document
```

The editing API also supports removing values, renaming or adding properties,
inserting array elements, and adding or removing comments. A property path can
specify an occurrence index when an object contains duplicate property names;
without one, the last matching property is selected.

Add a comment before or after the node selected by a path:

```kotlin
val document = Json5.parseToDocument(source)
val path = Json5Path["server"]["port"]

val before = document.addCommentBefore(path, "TCP port")
val after = before.document.addCommentAfter(
    path,
    "May be overridden",
    kind = Json5CommentKind.Block,
)
```

Use `removeComment` with a comment from the same document:

```kotlin
val comment = document.comments.first()
val result = document.removeComment(comment)
```

Adding a node and attaching a comment are separate operations. Run the comment
operation on the document returned by the insertion:

```kotlin
val inserted = document.putProperty(
    Json5Path.Root,
    "timeout",
    JsonPrimitive(30),
)
val commented = inserted.document.addCommentBefore(
    Json5Path["timeout"],
    "Seconds",
)
val editedText = commented.text
```

The high-level API does not currently update comment content. Replace the full
comment range with a `Json5TextEdit`, then parse the updated text:

```kotlin
val comment = document.comments.first()
val replacement = when (comment.kind) {
    Json5CommentKind.Line -> "// Updated comment"
    Json5CommentKind.Block -> "/* Updated comment */"
}
val edit = Json5TextEdit(
    offset = comment.range.start,
    length = comment.range.length,
    content = replacement,
)
val editedText = document.source.applyJson5Edits(listOf(edit))
val updatedDocument = Json5.parseToDocument(editedText)
```

Each `Json5EditResult.edits` list is relative to the document used for that
operation. Do not concatenate edits from chained operations. Use the final
`text`, or apply each edit list before starting the next operation.

`Json5Object` and `Json5Array` are iterable, and arrays also support indexed
access with `array[index]`.

## Additional

`parseToJsonElement` intentionally ignores comments because `JsonElement`
cannot represent them. Use `parseToDocument` whenever comments or original
source syntax must be preserved.
