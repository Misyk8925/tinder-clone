package com.tinder.subscriptions.subscription;

import com.tinder.subscriptions.profiles.ProfilesWebClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final ProfilesWebClient profilesWebClient;
}
