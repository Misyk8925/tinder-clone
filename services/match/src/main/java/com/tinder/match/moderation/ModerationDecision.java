package com.tinder.match.moderation;

public record ModerationDecision(String decision, String reason) {
    public static ModerationDecision allow() {
        return new ModerationDecision("ALLOW", null);
    }

    public boolean blocked() {
        return "BLOCK".equals(decision);
    }
}
