package differencing;

import org.apache.commons.lang.time.StopWatch;

import java.util.HashMap;
import java.util.Map;

public class StopWatches {
    public static Map<String, StopWatch> stopWatches = new HashMap<>();

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

    public static void stop(String name) {
        stopWatches.get(name).stop();
    }

    public static float getTime(String name) {
        return stopWatches.get(name).getTime() / 1000f;
    }
}
