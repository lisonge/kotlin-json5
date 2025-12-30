# kotlin-json5

[![Maven Central](https://img.shields.io/maven-central/v/li.songe/json5.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/li.songe/json5)
[![License](http://img.shields.io/:License-Apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

kotlin multiplatform [json5](https://json5.org) for [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)

complete multiplatform support: jvm/js/native

it not support comments(ignored) in json5 strings

## Usage

```kotlin
// latest -> https://github.com/lisonge/kotlin-json5/releases
implementation("li.songe:json5:latest")
```

## Decode

Json5String -> JsonElement

```kotlin
// import li.songe.json5.Json5

val element = Json5.parseToJson5Element("{a:1}")
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

### Decode Json5Token

Json5String -> Json5Token

```kotlin
val (element, ranges) = Json5.parseToJson5ElementAndRanges("{a:1}")
```

Loose Json5String -> Json5Token

```kotlin
val ranges = Json5.parseToJson5LooseRanges("{a:1,]{")
```

## Encode

JsonElement -> Json5String

```kotlin
// import li.songe.json5.Json5

val formatted: String = Json5.encodeToString(element)
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
