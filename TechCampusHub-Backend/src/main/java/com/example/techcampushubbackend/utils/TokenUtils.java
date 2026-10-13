package com.example.techcampushubbackend.utils;

import java.security.SecureRandom;

/**
 * Generates cryptographically secure opaque tokens used for e-mail
 * verification and password-reset links.
 */
public final class TokenUtils {

    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int TOKEN_LENGTH = 48;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenUtils() {
    }

    public static String randomToken() {
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
