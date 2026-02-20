package com.tinder.match.match.repository;

import com.tinder.match.match.model.Match;
import com.tinder.match.match.model.MatchId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, MatchId> {
}
