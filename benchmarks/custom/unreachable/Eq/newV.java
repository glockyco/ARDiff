package demo.benchmarks.custom.unreachable.Eq;
public class newV{
    public static double snippet(int n) {
        double result = 0;
        if (n < 0) {
            if (n > 0) {
                result = 0; // unreachable
            } else {
                result = -0.5; // n < 0
            }
        } else {
            result = 0.5; // n >= 0
        }
        return result;
    }
}
