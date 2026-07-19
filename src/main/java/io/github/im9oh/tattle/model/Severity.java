package io.github.im9oh.tattle.model;

import java.util.Locale;

/** Report severity. Ordinal order matters: LOW < MEDIUM < HIGH < CRITICAL. */
public enum Severity {
    LOW(0x2ECC71, "🟢"),      // green circle
    MEDIUM(0xF1C40F, "🟡"),   // yellow circle
    HIGH(0xE67E22, "🟠"),     // orange circle
    CRITICAL(0xE74C3C, "🔴"); // red circle

    private final int color;
    private final String emoji;

    Severity(int color, String emoji) {
        this.color = color;
        this.emoji = emoji;
    }

    public int color() {
        return color;
    }

    public String emoji() {
        return emoji;
    }

    public boolean atLeast(Severity other) {
        return ordinal() >= other.ordinal();
    }

    public static Severity parse(String value, Severity fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
