package com.tinder.profiles.profile.dto.errors;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ErrorSummary {
    private String message;
    private String code;
    private final String timestamp = LocalDateTime.now().toString();
    /**
     * Micrometer traceId from MDC.
     * Use it to find all related logs in ELK (filter by traceId) or in Zipkin.
     */
    @Builder.Default
    private String traceId = MDC.get("traceId");
}
