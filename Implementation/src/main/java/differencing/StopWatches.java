package differencing;

import org.apache.commons.lang.time.StopWatch;

import java.util.HashMap;
import java.util.Map;

public class StopWatches {
    public static Map<String, StopWatch> stopWatches = new HashMap<>();
    public static Map<String, Float> splits = new HashMap<>();

    public static void start(String name) {
        assert !stopWatches.containsKey(name);
        stopWatches.put(name, new StopWatch());
        stopWatches.get(name).start();
    }

    public static void suspend(String name) {
        stopWatches.get(name).suspend();
    }

    public static void resume(String name) {
        stopWatches.get(name).resume();
    }

    public static void split(String name, String splitName) {
        assert !splits.containsKey(splitName);
        splits.put(splitName, stopWatches.get(name).getTime() / 1000f);
    }

    public static void stop(String name) {
        stopWatches.get(name).stop();
    }

    public static float getTime(String name) {
        return stopWatches.get(name).getTime() / 1000f;
    }

    public static float getSplitTime(String name) {
        return splits.get(name);
    }

    public static float splitAndGetTime(String name, String splitName) {
        split(name, splitName);
        return getSplitTime(splitName);
    }

    public static float stopAndGetTime(String name) {
        stop(name);
        return getTime(name);
    }
}
