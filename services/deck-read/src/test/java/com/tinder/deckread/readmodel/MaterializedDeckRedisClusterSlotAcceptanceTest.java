package com.tinder.deckread.readmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("acceptance")
@DisplayName("Feature: materialized viewer operations fit one Redis Cluster slot")
class MaterializedDeckRedisClusterSlotAcceptanceTest {

    @Test
    @DisplayName("Scenario: staging, commit, page read and immediate exclusions use the same viewer hash tag")
    void everyLuaKeyUsesTheViewerHashTag() {
        UUID viewer = UUID.randomUUID();
        String expectedTag = viewer.toString();

        List<String> keys = List.of(
                ReadModelKeys.materializedMeta(viewer),
                ReadModelKeys.materializedOrder(viewer, 1),
                ReadModelKeys.materializedCards(viewer, 1),
                ReadModelKeys.materializedTail(viewer, 1),
                ReadModelKeys.materializedOrder(viewer, 2),
                ReadModelKeys.materializedCards(viewer, 2),
                ReadModelKeys.materializedTail(viewer, 2),
                ReadModelKeys.swipes(viewer),
                ReadModelKeys.matched(viewer),
                ReadModelKeys.suppressed(viewer));

        assertThat(keys).allSatisfy(key -> assertThat(hashTag(key)).isEqualTo(expectedTag));
    }

    private String hashTag(String key) {
        int open = key.indexOf('{');
        int close = key.indexOf('}', open + 1);
        assertThat(open).isNotNegative();
        assertThat(close).isGreaterThan(open + 1);
        return key.substring(open + 1, close);
    }
}
