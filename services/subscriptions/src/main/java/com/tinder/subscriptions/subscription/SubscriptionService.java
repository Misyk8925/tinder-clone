package com.tinder.subscriptions.subscription;

import com.tinder.subscriptions.profiles.ProfilesWebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionService {

    private final ProfilesWebClient profilesWebClient;


}
