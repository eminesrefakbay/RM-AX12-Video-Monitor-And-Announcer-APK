package dev.ax12.announcer;

/** Where spoken messages go. Implemented by the Android text-to-speech wrapper. */
public interface Announcer {
    /** @param urgent true = interrupt whatever is being spoken */
    void say(String text, boolean urgent);
}
