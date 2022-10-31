package differencing;

import org.apache.commons.lang.time.StopWatch;

public class RunTimer {
    public static StopWatch stopWatch = new StopWatch();

    public static void start() {
        stopWatch.start();;
    }

    public static void split() {
        stopWatch.split();
    }

    public static void stop() {
        stopWatch.stop();
    }

    public static float getTime() {
        return stopWatch.getTime() / 1000f;
    }

    public static float getSplitTime() {
        return stopWatch.getSplitTime() / 1000f;
    }
}
