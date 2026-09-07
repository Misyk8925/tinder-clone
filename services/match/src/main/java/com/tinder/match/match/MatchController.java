package com.tinder.match.match;

import com.tinder.match.match.dto.MatchResponseDto;
import com.tinder.match.security.CallerProfileResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/match")
public class MatchController {

    private final MatchService matchService;
    private final CallerProfileResolver callerProfileResolver;

    /**
     * Returns the authenticated user's own matches. The profile ID stays in the path for
     * backwards compatibility with existing clients, but it is validated against the caller's
     * token rather than trusted — otherwise anyone could read anyone else's match list.
     */
    @GetMapping("/{profileId}")
    public List<MatchResponseDto> getMyMatches(
            @PathVariable UUID profileId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID callerProfileId = callerProfileResolver.requireProfileId(jwt);
        if (!callerProfileId.equals(profileId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Matches can only be read for the authenticated profile");
        }
        return matchService.getMyMatches(callerProfileId);
    }
}
