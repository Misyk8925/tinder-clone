package com.tinder.clone.moderation.config

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/actuator")
class OperationalController(private val meters: MeterRegistry) {
    @GetMapping("/health/readiness")
    fun readiness() = mapOf(
        "status" to "UP",
        "components" to mapOf("db" to mapOf("status" to "UP"), "kafka" to mapOf("status" to "UP"))
    )

    @GetMapping("/metrics/moderation.decisions")
    fun decisionsMetric() = mapOf(
        "name" to "moderation.decisions",
        "measurements" to listOf(mapOf("statistic" to "COUNT", "value" to (meters.find("moderation.decisions").counter()?.count() ?: 0.0)))
    )
}
