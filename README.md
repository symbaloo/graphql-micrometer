Micrometer metrics GraphQL Introspection
========================================

With [graphql-micrometer] you can create export metrics from your GraphQL schema
to [micrometer](https://micrometer.io). Using
[graphql-java](https://www.graphql-java.com) instrumentation we can precisely
measure how queries and data fetchers are executed.

### Installation

Add this dependency to `build.gradle` or `build.gradle.kts`.

```kotlin
implementation("com.symbaloo:graphql-micrometer:1.0.0")
```

And then specify it when you build the `Graphql` object:

#### Kotlin:

```kotlin
    GraphQL.newGraphQL(schema)
        .instrumentation(MicrometerInstrumentation(meterRegistry))
        .build()
```

#### Java:

```java
    GraphQL.newGraphQL(schema)
        .instrumentation(new MicrometerInstrumentation(meterRegistry))
        .build();
```

Usually the meter registry will be obtained from the framework you're using, for
example through spring dependency injection.