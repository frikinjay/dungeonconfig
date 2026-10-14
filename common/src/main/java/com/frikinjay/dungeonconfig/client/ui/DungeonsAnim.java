package com.frikinjay.dungeonconfig.client.ui;

public final class DungeonsAnim {

    private DungeonsAnim() {}

    public static long millis() {
        return System.nanoTime() / 1_000_000L;
    }

    public static float clamp01(float t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    public static float easeOut(float t) {
        t = clamp01(t);
        float f = 1 - t;
        return 1 - f * f * f;
    }

    public static float easeInOut(float t) {
        t = clamp01(t);
        return t < 0.5F ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    public static float easeBack(float t) {
        t = clamp01(t);
        float c1 = 1.4F, c3 = c1 + 1;
        float f = t - 1;
        return 1 + c3 * f * f * f + c1 * f * f;
    }

    public static float approach(float current, float target, float speed, float dt) {
        float k = 1 - (float) Math.exp(-speed * dt);
        float next = current + (target - current) * k;
        return Math.abs(target - next) < 0.01F ? target : next;
    }

    public static final class Smooth {
        public float value;
        private float target;
        private boolean started;
        private final float speed;

        public Smooth(float speed) {
            this.speed = speed;
        }

        public void set(float target) {
            this.target = target;
            if (!started) {
                value = target;
                started = true;
            }
        }

        public void snap(float target) {
            this.target = target;
            this.value = target;
            this.started = true;
        }

        public void reset() {
            started = false;
        }

        public float update(float dt) {
            value = approach(value, target, speed, dt);
            return value;
        }

        public float target() {
            return target;
        }
    }

    public static final class Timer {
        private long start = Long.MIN_VALUE;

        public void start() {
            start = millis();
        }

        public boolean running(long duration) {
            return start != Long.MIN_VALUE && millis() - start < duration;
        }

        public float progress(long duration) {
            if (start == Long.MIN_VALUE) return 1;
            return clamp01((millis() - start) / (float) duration);
        }
    }
}
