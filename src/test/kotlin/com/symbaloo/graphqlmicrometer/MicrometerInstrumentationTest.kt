package com.symbaloo.graphqlmicrometer

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import graphql.GraphQL
import graphql.execution.instrumentation.Instrumentation
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.cumulative.CumulativeTimer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test

private val schema = """
    |type Query {
    |    simple: Int
    |    foo: Foo
    |}
    |type Foo {
    |    bar: String
    |    length: Int
    |}
    |""".trimMargin()

private data class Foo(val bar: String)

internal class MicrometerInstrumentationTest {

    private fun createTestSchema(instrumentation: Instrumentation): GraphQL {
        val typeDefinitionRegistry = SchemaParser().parse(schema)
        val runtimeWiring = RuntimeWiring.newRuntimeWiring()
            .type("Query") { builder ->
                builder.dataFetcher("simple") { 42 }
                builder.dataFetcher("foo") { Foo("world") }
            }
            .type("Foo") { builder ->
                builder.dataFetcher("length") { it.getSource<Foo>().bar.length }
            }
            .build()

        val schema = SchemaGenerator()
            .makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)
        return GraphQL
            .newGraphQL(schema)
            .instrumentation(instrumentation)
            .build()
    }

    private val simpleQuery = "{ simple }"
    private val simpleQueryHash = "fcb5db1824f01a7b70e29c80e611d42c9c2bb2a4d02ff654c77bbbaa5310e59b"

    @Test
    fun `create instance of MicrometerInstrumentation`() {
        val registry = SimpleMeterRegistry()
        MicrometerInstrumentation(registry)
    }

    @Test
    fun `query timers`() {
        val registry = SimpleMeterRegistry()
        val instrumentation = MicrometerInstrumentation(registry)
        val schema = createTestSchema(instrumentation)

        val result = schema.execute(simpleQuery)
        assertThat(result.isDataPresent).isTrue()

        assertThat(registry.fetchTimerQuery("execution")).isNotNull().all {
            transform { it.count() }.isEqualTo(1)
            transform { it.id.getTag("operationName") }.isEqualTo("__UNKNOWN__")
            transform { it.id.getTag("sha256") }.isEqualTo(simpleQueryHash)
        }

        assertThat(registry.fetchTimerQuery("parse")).isNotNull().all {
            transform { it.count() }.isEqualTo(1)
            transform { it.id.getTag("operationName") }.isEqualTo("__UNKNOWN__")
            transform { it.id.getTag("sha256") }.isEqualTo(simpleQueryHash)
        }

        assertThat(registry.fetchTimerQuery("validation")).isNotNull().all {
            transform { it.count() }.isEqualTo(1)
            transform { it.id.getTag("operationName") }.isEqualTo("__UNKNOWN__")
            transform { it.id.getTag("sha256") }.isEqualTo(simpleQueryHash)
        }
    }

    @Test
    fun `query timers count the number of executions`() {
        val registry = SimpleMeterRegistry()
        val instrumentation = MicrometerInstrumentation(registry)
        val schema = createTestSchema(instrumentation)

        repeat(5) {
            val result = schema.execute(simpleQuery)
            assertThat(result.isDataPresent).isTrue()
        }

        assertThat(registry.fetchTimerQuery("execution"))
            .isNotNull()
            .transform { it.count() }
            .isEqualTo(5)
    }

    @Test
    fun `resolver timer`() {
        val registry = SimpleMeterRegistry()
        val instrumentation = MicrometerInstrumentation(registry)
        val schema = createTestSchema(instrumentation)

        val result = schema.execute(simpleQuery)
        assertThat(result.isDataPresent).isTrue()

        assertThat(registry.fetchResolver("Query", "simple"))
            .isNotNull()
            .all {
                transform { it.count() }.isEqualTo(1)
                transform { it.id.getTag("sha256") }.isEqualTo(simpleQueryHash)
            }
    }

    @Test
    fun `trivial data fetchers are skipped`() {
        val registry = SimpleMeterRegistry()
        val instrumentation = MicrometerInstrumentation(registry)
        val schema = createTestSchema(instrumentation)

        val result = schema.execute("{ foo { bar length } }")
        assertThat(result.isDataPresent).isTrue()

        assertThat(registry.fetchResolver("Foo", "bar")).isNull()
        assertThat(registry.fetchResolver("Foo", "length")).isNotNull()
    }

    @Test
    fun `operationName is used as tag`() {
        val registry = SimpleMeterRegistry()
        val instrumentation = MicrometerInstrumentation(registry)
        val schema = createTestSchema(instrumentation)

        val result = schema.execute {
            it
                .query("query FooBar { foo { length } }")
                .operationName("FooBar")
        }
        assertThat(result.isDataPresent).isTrue()

        assertThat(registry.fetchTimerQuery("execution"))
            .isNotNull()
            .transform { it.id.getTag("operationName") }
            .isEqualTo("FooBar")

        assertThat(registry.fetchResolver("Foo", "length"))
            .isNotNull()
            .transform { it.id.getTag("operationName") }
            .isEqualTo("FooBar")
    }

    @Test
    fun `operationName is read from query`() {
        val registry = SimpleMeterRegistry()
        val instrumentation = MicrometerInstrumentation(registry)
        val schema = createTestSchema(instrumentation)

        val result = schema.execute("query FooBar { foo { length } }")
        assertThat(result.isDataPresent).isTrue()

        assertThat(registry.fetchTimerQuery("execution"))
            .isNotNull()
            .transform { it.id.getTag("operationName") }
            .isEqualTo("FooBar")

        assertThat(registry.fetchResolver("Foo", "length"))
            .isNotNull()
            .transform { it.id.getTag("operationName") }
            .isEqualTo("FooBar")
    }

    private fun MeterRegistry.fetchTimerQuery(operation: String): CumulativeTimer? =
        meters
            .find { it.id.name == "graphql.timer.query" && it.id.getTag("operation") == operation }
            ?.let { it as CumulativeTimer }

    private fun MeterRegistry.fetchResolver(parent: String, field: String): CumulativeTimer? =
        meters
            .find {
                it.id.name == "graphql.timer.resolver" &&
                    it.id.getTag("parent") == parent &&
                    it.id.getTag("field") == field
            }
            ?.let { it as CumulativeTimer }
}
