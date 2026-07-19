package io.github.im9oh.tattle.monitor;

import io.github.im9oh.tattle.TattlePlugin;
import io.github.im9oh.tattle.inspect.ConsoleInspector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * Observes console output by attaching a Log4j appender to the root logger and
 * feeding each line to {@link ConsoleInspector}.
 *
 * append() runs on whatever thread produced the log line, so it must be fast,
 * must never observe Tattle's own output (feedback loop), and must never throw.
 */
public final class ConsoleMonitor extends AbstractAppender {

    private final TattlePlugin plugin;
    private final ConsoleInspector inspector;

    public ConsoleMonitor(TattlePlugin plugin, ConsoleInspector inspector) {
        super("TattleConsoleMonitor", null, null, true, Property.EMPTY_ARRAY);
        this.plugin = plugin;
        this.inspector = inspector;
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
            if (!plugin.settings().consoleEnabled) {
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
            inspector.inspect(message, String.valueOf(event.getLevel()));
        } catch (Throwable t) {
            // Deliberately swallowed: monitoring must never break server logging.
        }
    }
}
