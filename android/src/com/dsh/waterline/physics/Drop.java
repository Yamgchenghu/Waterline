package com.dsh.waterline.physics;

/** One water droplet. Mass: 18.75 g (75% of 25 g). */
public final class Drop {
    public double x, y, vx, vy;
    public double radius;
    public double angle;
    public double spin;
    public int index;
    public boolean removed;

    /** Entered the cup: counted once, frozen while it fades out. */
    public boolean collected;
    public boolean poison;
    public boolean sleeping;
    public double restTime;
    public boolean ghost;
    public double fade = 0.0; // seconds left, 1.5 at entry
    public double horizForce = 0.0; // temporary initial force (N)
    public double horizForceTime = 0.0;
    public boolean secondaryRelease = false;
    public boolean bubbleTriggered = false;

    public static final double MASS = 0.0125;
    public static final double INV_MASS = 1.0 / MASS;

    public Drop(double x, double y, double radius, int index) {
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.index = index;
        this.angle = Math.random() * Math.PI * 2.0;
        this.spin = (Math.random() - 0.5) * 2.0;
    }
}
