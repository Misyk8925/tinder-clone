package com.tinder.clone.consumer.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Logback filter that suppresses Hibernate SQL debug logs for the outbox polling
 * query.  The outbox scheduler runs every 100 ms and would otherwise flood the
 * console with identical SELECT … FROM profile_event_outbox … FOR UPDATE SKIP LOCKED
 * statements.
 *
 * Applied per-appender in logback-spring.xml — has no effect on any other SQL.
 */
public class OutboxSqlFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        String msg = event.getFormattedMessage();
        if (msg != null && (msg.contains("match_event_outbox") || msg.contains("swipe_event_outbox"))) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}

