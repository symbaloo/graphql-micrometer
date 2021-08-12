# Micrometer metrics GraphQL Introspection

With **graphql-micrometer** you can create export metrics from your GraphQL schema
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
val graphql = GraphQL.newGraphQL(schema)
    .instrumentation(MicrometerInstrumentation(meterRegistry))
    .build()
```

#### Java:

```java
class Main {
    GraphQL graphql = GraphQL.newGraphQL(schema)
        .instrumentation(new MicrometerInstrumentation(meterRegistry))
        .build();
}
```

Usually the meter registry will be obtained from the framework you're using, for
example through spring dependency injection.

### Metrics

For example, a query such as

```graphql
query Translations {
  translations {
    value
  }
}
```

Would report something like this, when using the [prometheus meter registry](https://micrometer.io/docs/registry/prometheus).

```
# HELP graphql_timer_query_seconds Timer that records the time to fetch the data by Operation Name
# TYPE graphql_timer_query_seconds gauge
graphql_timer_query_seconds_count{application="web",operation="validation",operationName="Translations",sha256="54d4922bf8a3a542b0eb2b6d47fff7466f77ea793fc875a5067b02b43411f710",} 1.0
graphql_timer_query_seconds_max{application="web",operation="validation",operationName="Translations",sha256="54d4922bf8a3a542b0eb2b6d47fff7466f77ea793fc875a5067b02b43411f710",} 0.051620757
graphql_timer_query_seconds_sum{application="web",operation="validation",operationName="Translations",sha256="54d4922bf8a3a542b0eb2b6d47fff7466f77ea793fc875a5067b02b43411f710",} 0.051620757

graphql_timer_query_seconds_count{application="web",operation="parse",operationName="Translations",sha256="54d4922bf8a3a542b0eb2b6d47fff7466f77ea793fc875a5067b02b43411f710",} 1.0
graphql_timer_query_seconds_max{application="web",operation="parse",operationName="Translations",sha256="54d4922bf8a3a542b0eb2b6d47fff7466f77ea793fc875a5067b02b43411f710",} 0.057602257
graphql_timer_query_seconds_sum{application="web",operation="parse",operationName="Translations",sha256="54d4922bf8a3a542b0eb2b6d47fff7466f77ea793fc875a5067b02b43411f710",} 0.057602257

graphql_timer_query_seconds_count{application="web",operation="execution",operationName="Translations",sha256="54d4922bf8a3a542b0eb2b6d47fff7466f77ea793fc875a5067b02b43411f710",} 3.0
graphql_timer_query_seconds_sum{application="web",operation="execution",operationName="Translations",sha256="54d4922bf8a3a542b0eb2b6d47fff7466f77ea793fc875a5067b02b43411f710",} 6.70924729
graphql_timer_query_seconds_max{application="web",operation="execution",operationName="Translations",sha256="54d4922bf8a3a542b0eb2b6d47fff7466f77ea793fc875a5067b02b43411f710",} 3.651674457

# HELP graphql_timer_resolver_seconds Timer that records the time to fetch the data by Operation Name
# TYPE graphql_timer_resolver_seconds gauge
graphql_timer_resolver_seconds_max{application="web",field="value",operationName="Translations",parent="Translation",sha256="54d4922bf8a3a542b0eb2b6d47fff7466f77ea793fc875a5067b02b43411f710",} 0.00250673
graphql_timer_resolver_seconds_max{application="web",field="translations",operationName="Translations",parent="Query",sha256="54d4922bf8a3a542b0eb2b6d47fff7466f77ea793fc875a5067b02b43411f710",} 3.421262552
graphql_timer_resolver_seconds_max{application="web",field="id",operationName="Translations",parent="Translation",sha256="54d4922bf8a3a542b0eb2b6d47fff7466f77ea793fc875a5067b02b43411f710",} 3.01568E-4
graphql_timer_resolver_seconds_count{application="web",field="value",operationName="Translations",parent="Translation",sha256="54d4922bf8a3a542b0eb2b6d47fff7466f77ea793fc875a5067b02b43411f710",} 340.0
graphql_timer_resolver_seconds_sum{application="web",field="value",operationName="Translations",parent="Translation",sha256="54d4922bf8a3a542b0eb2b6d47fff7466f77ea793fc875a5067b02b43411f710",} 0.01424429
graphql_timer_resolver_seconds_count{application="web",field="translations",operationName="Translations",parent="Query",sha256="54d4922bf8a3a542b0eb2b6d47fff7466f77ea793fc875a5067b02b43411f710",} 3.0
graphql_timer_resolver_seconds_sum{application="web",field="translations",operationName="Translations",parent="Query",sha256="54d4922bf8a3a542b0eb2b6d47fff7466f77ea793fc875a5067b02b43411f710",} 6.198119625
```

There are two types of metrics: one for query execution, and another for
resolver timing. The timers are registered to micrometer using these names by
default:

- `graphql.timer.query`
- `graphql.timer.resolver`

#### Query execution metrics

What we see here, is that there are three timers for each unique query that's executed:

- query execution time
- query validation time
- query parsing time

#### Resolver execution metrics

Then, each (non)trivial data fetcher (resolver) has a timer too. The `parent`
tag indicates the GraphQL type from the schema, and the `field` is the field in
that type.
