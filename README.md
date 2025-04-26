# kotlin-json5

kotlin multiplatform json5 for [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)

its principle is to convert a JSON5 string into a valid JsonElement object, which facilitates interoperability with kotlinx serialization

## Usage

```kotlin
implementation("li.songe:json5:0.0.1")
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

## Encode

JsonElement -> Json5String

```kotlin
// import li.songe.json5.Json5

val formatted: String = Json5.encodeToString(element, 2)
```

Object -> Json5String

```kotlin
// import kotlinx.serialization.json.Json
// import kotlinx.serialization.Serializable
// import li.songe.json5.encodeToJson5String

val json = Json {
  // add your json config
  ignoreUnknownKeys = true
}

@Serializable
data class A(val id:Int)

val formatted: String = json.encodeToJson5String(A(id=0))
```
