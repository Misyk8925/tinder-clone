package com.tinder.profiles.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Set;

/**
 * Resolves X.509 client certificate CN to a UserDetails.
 * Allowed CNs are internal microservices that are permitted
 * to call the /internal/** endpoints via mTLS.
 */
public class MtlsUserDetailsService implements UserDetailsService {

    /** Known internal service CNs allowed to call /internal endpoints */
    private static final Set<String> ALLOWED_CNS = Set.of(
            "deck-service"
    );

    @Override
    public UserDetails loadUserByUsername(String cn) throws UsernameNotFoundException {
        if (!ALLOWED_CNS.contains(cn)) {
            throw new UsernameNotFoundException(
                    "Certificate CN '" + cn + "' is not an authorized internal service");
        }
        // Grant ROLE_INTERNAL_CLIENT so SecurityConfig can restrict access
        return new User(cn, "", List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_CLIENT")));
    }
}

