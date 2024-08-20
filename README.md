# kotlin-json5

kotlin multiplatform json5 for [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)

## usage

```kotlin
implementation("li.songe:json5:0.0.1")
```

Json5String -> JsonElement

```kotlin
val element = Json5.parseToJson5Element("{a:1}")
```

JsonElement -> Json5String

```kotlin
val formatted: String = Json5.encodeToString(element, 2)
```

interop with `kotlinx.serialization`

```kotlin
val json = Json {
  // add your json config
  ignoreUnknownKeys = true
}
data class A(val id:Int)

// Json5String -> T
val a = json.decodeFromJson5String<A>("{id:0, b:''}")

// T -> Json5String
val formatted: String = json.encodeToJson5String(a)
```
