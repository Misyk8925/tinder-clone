package com.tinder.deck.experiment;

import java.util.UUID;

/**
 * What one viewer produced during an experiment window, as the consumer service recorded
 * it. Counts only: no latent or scoring value appears here, so an outcome can never be
 * computed from anything but observed behaviour.
 */
public record ViewerOutcome(UUID viewerId, long swipes, long likes, long matches) {
}
