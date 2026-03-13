package com.tinder.profiles.profile.dto.errors;

import lombok.Getter;


@Getter
public class CustomErrorResponse {
    private final ErrorSummary error;
    private final ErrorDetails details;
    /** Micrometer traceId — use it to find all related logs in ELK / Zipkin. */
    private final String traceId;

    public CustomErrorResponse(ErrorSummary error, ErrorDetails details) {
        this.error   = error;
        this.details = details;
        this.traceId = org.slf4j.MDC.get("traceId");  // populated by Micrometer Tracing
    }
}
