package com.shortlink.util;

/**
 * System Design Pattern: Base62 Encoding
 * Converts monotonically increasing numeric IDs into short, URL-safe strings.
 */
public final class Base62Encoder {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();
    // Offset ensures short codes start at 4+ characters instead of single digits
    private static final long ID_OFFSET = 100_000L;

    private Base62Encoder() {}

    public static String encode(long id) {
        long value = id + ID_OFFSET;
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            sb.append(ALPHABET.charAt((int) (value % BASE)));
            value /= BASE;
        }
        return sb.reverse().toString();
    }
}
