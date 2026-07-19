package io.github.im9oh.tattle.monitor;

import io.github.im9oh.tattle.TattlePlugin;
import io.github.im9oh.tattle.config.Settings;
import io.github.im9oh.tattle.model.Observation;
import io.github.im9oh.tattle.model.RegexRule;
import io.github.im9oh.tattle.model.SourceType;
import io.github.im9oh.tattle.score.ScoringEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * Observes console output by attaching a Log4j appender to the root logger.
 * Matches configurable regex rules against each log line.
 *
 * append() runs on whatever thread produced the log line, so it must be fast,
 * must never log through a path that reaches this appender for its own
 * messages (Tattle's own logger is filtered out to prevent feedback loops),
 * and must never throw.
 */
public final class ConsoleMonitor extends AbstractAppender {

    private final TattlePlugin plugin;
    private final ScoringEngine engine;

    public ConsoleMonitor(TattlePlugin plugin, ScoringEngine engine) {
        super("TattleConsoleMonitor", null, null, true, Property.EMPTY_ARRAY);
        this.plugin = plugin;
        this.engine = engine;
    }

    public void attach() {
        start();
        ((Logger) LogManager.getRootLogger()).addAppender(this);
    }

    public void detach() {
        ((Logger) LogManager.getRootLogger()).removeAppender(this);
        stop();
    }

    @Override
    public void append(LogEvent event) {
        try {
            Settings settings = plugin.settings();
            if (!settings.consoleEnabled) {
                return;
            }
            // Never observe our own output — that would loop reports forever.
            String loggerName = event.getLoggerName();
            if (loggerName != null && loggerName.contains("Tattle")) {
                return;
            }
            String message = event.getMessage().getFormattedMessage();
            if (message == null || message.isEmpty() || message.contains("[Tattle]")) {
                return;
            }

            for (RegexRule rule : settings.consoleRules) {
                if (rule.pattern().matcher(message).find()) {
                    engine.observe(Observation.of(SourceType.CONSOLE, "SERVER", null,
                            "console/" + rule.name(),
                            "Console matched rule '" + rule.name() + "'",
                            "[" + event.getLevel() + "] " + message,
                            rule.score(), rule.severity()));
                    return; // first matching rule wins; one log line = at most one observation
                }
            }
        } catch (Throwable t) {
            // Deliberately swallowed: monitoring must never break server logging.
        }
    }
}
