package com.tinder.deckread.dto;

public record ProblemDetails(
        String type,
        String title,
        int status,
        String code,
        String detail
) {
    public static ProblemDetails of(int status, String code, String title, String detail) {
        String slug = code.toLowerCase().replace('_', '-');
        return new ProblemDetails("https://tinder.example/problems/" + slug, title, status, code, detail);
    }
}
