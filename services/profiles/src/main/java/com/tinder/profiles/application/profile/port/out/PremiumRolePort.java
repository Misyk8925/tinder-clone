package com.tinder.profiles.application.profile.port.out;

/** Outbound identity-provider operations needed by premium membership flows. */
public interface PremiumRolePort {

    void grantPremium(String userId);

    void revokePremium(String userId);
}
