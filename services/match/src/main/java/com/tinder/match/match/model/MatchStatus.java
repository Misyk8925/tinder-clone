package com.tinder.match.match.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


public enum MatchStatus {

    ACTIVE,
    INACTIVE,
    BLOCKED,
    UNMATCHED
}