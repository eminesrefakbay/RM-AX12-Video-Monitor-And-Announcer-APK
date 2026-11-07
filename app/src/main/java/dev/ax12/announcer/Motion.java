package dev.ax12.announcer;

/**
 * One motion rule for the whole app: every fade and slide takes between 150 and 250 milliseconds, things coming in a little
 * longer than things going out. Plain Java, so a test can check the rule.
 */
public final class Motion {
    public static final int IN_MS = 220;          // a panel, a menu or a card coming in
    public static final int OUT_MS = 160;         // the same going out: quicker, so it never gets in the way
    public static final int SLIDE_DP = 24;        // how far a panel travels while it fades in or out

    public static final int MIN_MS = 150;
    public static final int MAX_MS = 250;

    private Motion() {}
}
