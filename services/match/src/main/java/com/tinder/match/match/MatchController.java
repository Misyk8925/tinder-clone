package com.tinder.match.match;

import com.tinder.match.match.dto.MatchResponseDto;
import lombok.RequiredArgsConstructor;
import com.tinder.match.security.AuthenticatedProfileResolver;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/match")
public class MatchController {

    private final MatchService matchService;
    private final AuthenticatedProfileResolver authenticatedProfileResolver;

    @GetMapping("/{profileId}")
    public List<MatchResponseDto> getMyMatches(@PathVariable UUID profileId,
                                               @AuthenticationPrincipal Jwt jwt) {
        if (!authenticatedProfileResolver.resolve(jwt).equals(profileId)) {
            throw new AccessDeniedException("Cannot read another profile's matches");
        }
        return matchService.getMyMatches(profileId);
    }

    @PostMapping
    public void createMatch() {
    }
}
