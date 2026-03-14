package com.tinder.match.match.repository;

import com.tinder.match.match.model.Match;
import com.tinder.match.match.model.MatchId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, MatchId> {

    @Query("SELECT m FROM Match m WHERE m.id.profile1Id = :profileId OR m.id.profile2Id = :profileId")
    List<Match> findByProfileId(@Param("profileId") UUID profileId);
}
