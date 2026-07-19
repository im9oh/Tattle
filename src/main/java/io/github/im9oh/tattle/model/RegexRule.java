package io.github.im9oh.tattle.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** A configurable regex rule with a score and severity. */
public record RegexRule(String name, Pattern pattern, double score, Severity severity) {

    /** Parses a list of {name, pattern, score, severity} maps from config; invalid entries are skipped with a warning. */
    public static List<RegexRule> parseList(List<Map<?, ?>> raw, String rulePath, Logger log) {
        List<RegexRule> rules = new ArrayList<>();
        if (raw == null) {
            return rules;
        }
        for (Map<?, ?> entry : raw) {
            Object name = entry.get("name");
            Object pattern = entry.get("pattern");
            if (name == null || pattern == null) {
                log.warning("Skipping rule in " + rulePath + " with missing name or pattern: " + entry);
                continue;
            }
            double score = entry.get("score") instanceof Number n ? n.doubleValue() : 1.0;
            Severity severity = Severity.parse(String.valueOf(entry.get("severity")), Severity.LOW);
            try {
                rules.add(new RegexRule(String.valueOf(name), Pattern.compile(String.valueOf(pattern)), score, severity));
            } catch (PatternSyntaxException e) {
                log.warning("Skipping rule '" + name + "' in " + rulePath + ": invalid regex: " + e.getMessage());
            }
        }
        return rules;
    }
}
