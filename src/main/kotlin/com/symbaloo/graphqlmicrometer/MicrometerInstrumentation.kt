package com.symbaloo.graphqlmicrometer

import graphql.ExecutionResult
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.execution.instrumentation.SimpleInstrumentationContext.whenCompleted
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters
import graphql.execution.instrumentation.tracing.TracingInstrumentation
import graphql.language.Document
import graphql.schema.GraphQLTypeUtil
import graphql.validation.ValidationError
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.security.MessageDigest

private const val QUERY_TIME_METRIC_NAME = "graphql.timer.query"
private const val RESOLVER_TIME_METRIC_NAME = "graphql.timer.resolver"
private const val OPERATION_NAME_TAG = "operationName"
private const val QUERY_HASH_TAG = "sha256"
private const val OPERATION = "operation"
private const val PARENT = "parent"
private const val FIELD = "field"
private const val TIMER_DESCRIPTION = "Timer that records the time to fetch the data by Operation Name"

private val queryNameRegex = Regex("(query|mutation)\\s+(\\w+)")

/**
 * See also:
 *
 * - https://github.com/graphql-java-kickstart/graphql-spring-boot/blob/master/graphql-spring-boot-autoconfigure/src/main/java/graphql/kickstart/autoconfigure/web/servlet/metrics/MetricsInstrumentation.java
 * - https://github.com/apollographql/apollo-tracing
 * - [TracingInstrumentation]
 */
class MicrometerInstrumentation(
    private val meterRegistry: MeterRegistry,
) : SimpleInstrumentation() {

    override fun createState(parameters: InstrumentationCreateStateParameters): InstrumentationState =
        TraceState(
            query = parameters.executionInput.query,
            operationName = parameters.executionInput.operationName,
        )

    override fun beginValidation(
        parameters: InstrumentationValidationParameters,
    ): InstrumentationContext<MutableList<ValidationError>> {
        val state = parameters.getInstrumentationState<TraceState>()
        val sample = Timer.start(meterRegistry)
        return whenCompleted { _, _ ->
            sample.stop(buildQueryTimer(state.operationName, state.hash, operation = "validation"))
        }
    }

    override fun beginParse(parameters: InstrumentationExecutionParameters): InstrumentationContext<Document> {
        val state = parameters.getInstrumentationState<TraceState>()
        val sample = Timer.start(meterRegistry)
        return whenCompleted { _, _ ->
            sample.stop(buildQueryTimer(state.operationName, state.hash, operation = "parse"))
        }
    }

    override fun beginExecution(
        parameters: InstrumentationExecutionParameters,
    ): InstrumentationContext<ExecutionResult> {
        val state = parameters.getInstrumentationState<TraceState>()
        val sample = Timer.start(meterRegistry)
        return whenCompleted { _, _ ->
            sample.stop(buildQueryTimer(state.operationName, state.hash, operation = "execution"))
        }
    }

    override fun beginFieldFetch(parameters: InstrumentationFieldFetchParameters): InstrumentationContext<Any> {
        val state = parameters.getInstrumentationState<TraceState>()

        if (parameters.isTrivialDataFetcher) {
            return super.beginFieldFetch(parameters)
        }

        val sample = Timer.start(meterRegistry)
        return whenCompleted { _, _ ->
            val parentType = GraphQLTypeUtil.simplePrint(parameters.executionStepInfo.parent.unwrappedNonNullType)
            val fieldName = parameters.executionStepInfo.fieldDefinition.name
            sample.stop(buildFieldTimer(state.operationName, state.hash, parentType, fieldName))
        }
    }

    private fun buildQueryTimer(operationName: String, hash: String, operation: String): Timer =
        Timer.builder(QUERY_TIME_METRIC_NAME)
            .description(TIMER_DESCRIPTION)
            .tag(OPERATION_NAME_TAG, operationName)
            .tag(QUERY_HASH_TAG, hash)
            .tag(OPERATION, operation)
            .register(meterRegistry)

    private fun buildFieldTimer(operationName: String, hash: String, parent: String, field: String): Timer =
        Timer.builder(RESOLVER_TIME_METRIC_NAME)
            .description(TIMER_DESCRIPTION)
            .tag(OPERATION_NAME_TAG, operationName)
            .tag(QUERY_HASH_TAG, hash)
            .tag(PARENT, parent)
            .tag(FIELD, field)
            .tag(OPERATION, "resolvers")
            .register(meterRegistry)
}

class TraceState(
    private val query: String,
    operationName: String?,
) : InstrumentationState {

    val hash: String = query.sha256()
    val operationName: String = operationName ?: findQueryName() ?: "__UNKNOWN__"

    private fun findQueryName(): String? =
        queryNameRegex.find(query)?.groups?.get(2)?.value?.removeSuffix(")")
}

private fun String.sha256(): String {
    val data = this.encodeToByteArray()
    return MessageDigest
        .getInstance("SHA-256")
        .run {
            update(data)
            digest()
        }
        .asHexString()
}

private fun ByteArray.asHexString() =
    joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
