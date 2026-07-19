package io.github.im9oh.tattle.model;

/** Where an observation came from. */
public enum SourceType {
    CHAT("Chat"),
    COMMAND("Command"),
    CONSOLE("Console"),
    ACTION("Action");

    private final String display;

    SourceType(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }
}
