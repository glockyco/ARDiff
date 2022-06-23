package demo.benchmarks.custom.loop.Eq;
public class oldV{
    public static int snippet(int n) {
        int i = 0;
        if (n < 0) {
            return 0;
        }
        while (i < n) {
            i++;
        }
        return i;
    }
}