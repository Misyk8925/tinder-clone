package com.tinder.profiles.profile;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdsQueryParamParserTest {

    private final IdsQueryParamParser parser = new IdsQueryParamParser();

    @Test
    void shouldParseValidIdsWithWhitespace() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        List<UUID> parsed = parser.parse(first + " , " + second);

        assertThat(parsed).containsExactly(first, second);
    }

    @Test
    void shouldRejectBlankIds() {
        assertThatThrownBy(() -> parser.parse("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void shouldRejectInvalidUuid() {
        assertThatThrownBy(() -> parser.parse("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectMoreThanMaxIds() {
        String ids = java.util.stream.Stream.generate(() -> UUID.randomUUID().toString())
                .limit(IdsQueryParamParser.MAX_IDS + 1L)
                .reduce((left, right) -> left + "," + right)
                .orElse("");

        assertThatThrownBy(() -> parser.parse(ids))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum");
    }
}

