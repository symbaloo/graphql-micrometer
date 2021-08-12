package com.symbaloo.graphqlmicrometer

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test

internal class MicrometerInstrumentationTest {

    @Test
    fun `create instance of MicrometerInstrumentation`() {
        val registry = SimpleMeterRegistry()
        MicrometerInstrumentation(registry)
    }
}
