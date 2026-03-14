package com.tinder.match.match;

import com.tinder.match.match.dto.MatchResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/match")
public class MatchController {

    private final MatchService matchService;

    @GetMapping("/{profileId}")
    public List<MatchResponseDto> getMyMatches(@PathVariable UUID profileId) {
        return matchService.getMyMatches(profileId);
    }

    @PostMapping
    public void createMatch() {
    }
}
