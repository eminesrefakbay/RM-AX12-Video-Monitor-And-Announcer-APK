package dev.ax12.announcer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Newest CRSF telemetry values, each group expiring on its own clock.
 *
 * Why per group: the radio's own transmitter module keeps sending link statistics even when the
 * aircraft is off, while battery, flight mode and GPS come from the aircraft. With one shared clock
 * the last flight mode ("RTH") would stay alive forever. Here a group is dropped when ITS frames
 * stop arriving for maxAgeMs, so a rule never acts on a stale reading.
 */
public final class CrsfState {

    private final CrsfParser parser = new CrsfParser();
    private final Map<String, Double> nums = new HashMap<String, Double>();
    private final Map<String, String> texts = new HashMap<String, String>();
    private final Map<Integer, Long> lastSeen = new HashMap<Integer, Long>();
    private final Map<Integer, Set<String>> keysByType = new HashMap<Integer, Set<String>>();

    /** @return true if the frame was understood */
    public boolean feed(byte[] frame, long nowMs) {
        Map<String, Double> n = new HashMap<String, Double>();
        Map<String, String> t = new HashMap<String, String>();
        if (!parser.feed(frame, n, t)) return false;
        int type = frame[2] & 0xFF;
        nums.putAll(n);
        texts.putAll(t);
        Set<String> keys = keysByType.get(type);
        if (keys == null) {
            keys = new HashSet<String>();
            keysByType.put(type, keys);
        }
        keys.addAll(n.keySet());
        keys.addAll(t.keySet());
        lastSeen.put(type, nowMs);
        return true;
    }

    /** Remove every group whose frames have not arrived for maxAgeMs. */
    public void expire(long nowMs, long maxAgeMs) {
        for (Map.Entry<Integer, Long> e : new HashMap<Integer, Long>(lastSeen).entrySet()) {
            if (nowMs - e.getValue() > maxAgeMs) {
                Set<String> keys = keysByType.get(e.getKey());
                if (keys != null) {
                    for (String k : keys) {
                        nums.remove(k);
                        texts.remove(k);
                    }
                }
                lastSeen.remove(e.getKey());
            }
        }
    }

    public Map<String, Double> numbers() { return nums; }
    public Map<String, String> texts() { return texts; }
    public int framesOk() { return parser.framesOk; }
    public int framesBadCrc() { return parser.framesBadCrc; }
    public int framesIgnored() { return parser.framesIgnored; }
}
