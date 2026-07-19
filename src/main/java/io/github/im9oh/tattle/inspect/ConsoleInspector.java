package io.github.im9oh.tattle.inspect;

import io.github.im9oh.tattle.TattleContext;
import io.github.im9oh.tattle.model.Observation;
import io.github.im9oh.tattle.model.RegexRule;
import io.github.im9oh.tattle.model.SourceType;
import io.github.im9oh.tattle.score.ScoringEngine;

/**
 * Inspects one console/log line against the configured patterns.
 * Shared between the Log4j-attached console monitor and the simulator.
 * Thread-safe.
 */
public final class ConsoleInspector {

    private final TattleContext ctx;
    private final ScoringEngine engine;

    public ConsoleInspector(TattleContext ctx, ScoringEngine engine) {
        this.ctx = ctx;
        this.engine = engine;
    }

    public void inspect(String line, String levelPrefix) {
        for (RegexRule rule : ctx.settings().consoleRules) {
            if (rule.pattern().matcher(line).find()) {
                engine.observe(Observation.of(SourceType.CONSOLE, "SERVER", null,
                        "console/" + rule.name(),
                        "Console matched rule '" + rule.name() + "'",
                        (levelPrefix != null ? "[" + levelPrefix + "] " : "") + line,
                        rule.score(), rule.severity()));
                return; // first matching rule wins; one log line = at most one observation
            }
        }
    }
}
