package com.tinder.profiles.architecture;

import com.tinder.profiles.domain.profile.Hobby;
import com.tinder.profiles.domain.profile.ProfileChangeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * The domain depends on nothing but itself and the JDK, so every vocabulary it
 * shares with other services is declared twice: once in {@code domain..} and once
 * in {@code tinder-contracts}. Adapters bridge the two by constant name, which no
 * compiler can check — the classes are never referenced together, so adding a
 * constant to one side alone compiles cleanly and fails only when a request
 * reaches the conversion.
 *
 * <p>These tests are that missing check. They fail during {@code mvn test},
 * naming the constant that drifted and the side it is missing from.
 */
@DisplayName("Shared vocabularies")
class SharedVocabularyTest {

    @Test
    @DisplayName("the domain and contract Hobby enums declare the same constants")
    void hobbyVocabulariesMatch() {
        assertSameConstants(
                Hobby.class, "domain.profile.Hobby",
                com.tinder.contracts.dto.Hobby.class, "contracts.dto.Hobby",
                "ProfilePersistenceMapper, ProfileApiMapper and SharedProfileRowMapper");
    }

    @Test
    @DisplayName("the domain and contract change-type enums declare the same constants")
    void changeTypeVocabulariesMatch() {
        assertSameConstants(
                ProfileChangeType.class, "domain.profile.ProfileChangeType",
                com.tinder.contracts.event.v1.ChangeType.class, "contracts.event.v1.ChangeType",
                "OutboxEventPublisherAdapter");
    }

    /**
     * Compares two name-bridged enums in both directions, so a failure reports the
     * drifted constants themselves rather than two full vocabularies to diff by eye.
     */
    private static <A extends Enum<A>, B extends Enum<B>> void assertSameConstants(
            Class<A> domainEnum, String domainName,
            Class<B> contractEnum, String contractName,
            String bridgedBy
    ) {
        Set<String> domain = constantsOf(domainEnum);
        Set<String> contract = constantsOf(contractEnum);

        then(domain)
                .as("%s declares no constants — the comparison against %s would pass vacuously",
                        domainName, contractName)
                .isNotEmpty();

        then(missingFrom(domain, contract))
                .as("declared in %s but missing from %s — add them there, "
                        + "or the name bridge in %s drops them at runtime",
                        domainName, contractName, bridgedBy)
                .isEmpty();

        then(missingFrom(contract, domain))
                .as("declared in %s but missing from %s — add them there, "
                        + "or the name bridge in %s drops them at runtime",
                        contractName, domainName, bridgedBy)
                .isEmpty();
    }

    private static <E extends Enum<E>> Set<String> constantsOf(Class<E> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> missingFrom(Set<String> source, Set<String> target) {
        Set<String> missing = new LinkedHashSet<>(source);
        missing.removeAll(target);
        return missing;
    }
}
