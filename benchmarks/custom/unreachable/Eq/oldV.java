package demo.benchmarks.custom.unreachable.Eq;
public class oldV{
    public static double snippet(int n) {
        double result = 0;
        int zero = 0;
        if (n < zero) {
            if (n > zero) {
                result = 0; // unreachable
            } else {
                result = -0.5; // n < 0
            }
        } else {
            result = 1.5; // n >= 0
        }
        return result;
    }
}
