package com.donutmoney.client.util;

public class MathUtil {
    public static double lerp(double delta, double start, double end) {
        return start + (end - start) * delta;
    }
}
