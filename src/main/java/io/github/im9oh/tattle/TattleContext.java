package io.github.im9oh.tattle;

import io.github.im9oh.tattle.config.Settings;

import java.util.logging.Logger;

/**
 * Minimal environment the core pipeline (inspectors, scoring, reporting,
 * webhook) needs to run. Implemented by the plugin on a live server and by
 * the standalone {@link io.github.im9oh.tattle.sim.Simulator} for offline testing.
 */
public interface TattleContext {

    Settings settings();

    Logger logger();
}
