package com.tinder.clone.moderation.infrastructure.provider

import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

internal object ProviderRestClientFactory {
    fun create(baseUrl: String, connectTimeout: Duration, readTimeout: Duration): RestClient {
        val httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(readTimeout)
        }
        return RestClient.builder()
            .baseUrl(baseUrl.removeSuffix("/"))
            .requestFactory(requestFactory)
            .build()
    }
}
