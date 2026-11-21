package com.dsh.waterline.physics;

/**
 * One rigid body. A line may be a curved polyline made of many segments, but
 * it is still a single body: one mass, one centre of mass, one rotation and
 * one inertia tensor. Dynamic mechanisms also use this class.
 *
 * Units: metres, kilograms, seconds. World Y grows downwards.
 */
public final class Bar {
    public double x, y;              // centre of mass, world
    public double vx, vy;
    public double angle;             // radians
    public double omega;

    public double[] localX = new double[2];
    public double[] localY = new double[2];
    public int pointCount = 2;
    public double totalLength;       // sum of polyline segments

    public double halfLen;           // half of the straight length (mechanisms)
    public double boundRadius;       // broad-phase bound around the COM
    public double radius;            // visual / collision thickness radius
    public double mass;
    public double invMass;
    public double inertia;
    public double invInertia;

    public double restitution = 0.12;
    public double friction = 0.30;

    public boolean fixed;
    public boolean pinned;
    public double pinLocalX, pinLocalY;
    public double pinX, pinY;

    public int tag;
    public int colorSeed;
    public int supportId = -1;
    public int ignoreSupportA = -1, ignoreSupportB = -1;
    public boolean waterPass = false;
    public boolean ground = false;
    public boolean noGround = false;
    public boolean calmEligible = false;
    public boolean sleeping = false;
    public boolean kinematic = false;
    public boolean circleShape = false;
    public boolean antiSqueeze = false;
    public boolean oneWay = false;
    public boolean oneWayArc = false;
    public boolean oneWayBlockInside = true;
    public double oneWayNx = 0, oneWayNy = -1;
    public boolean bouncy = false;
    public boolean breakable = false;
    public boolean inactive = false;
    public double breakCooldown = 0.0;
    public double prevX, prevY;
    public double prevAngle;
    public double restTime = 0.0;

    public Bar() {}

    public static Bar capsule(double x1, double y1, double x2, double y2,
                              double radius, double mass, boolean fixed) {
        Bar b = new Bar();
        b.setEndpoints(x1, y1, x2, y2);
        b.radius = radius;
        b.mass = mass;
        b.fixed = fixed;
        b.refreshMass();
        return b;
    }

    public static Bar polyline(double[] wx, double[] wy, int n, double radius,
                               double density, boolean fixed) {
        Bar b = new Bar();
        b.configurePolyline(wx, wy, n, radius, density, fixed);
        return b;
    }

    public void setEndpoints(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        x = (x1 + x2) * 0.5;
        y = (y1 + y2) * 0.5;
        angle = Math.atan2(dy, dx);
        halfLen = Math.max(0.001, len * 0.5);
        totalLength = len;
        pointCount = 2;
        localX = new double[]{-halfLen, halfLen};
        localY = new double[]{0, 0};
        boundRadius = halfLen + radius;
        cacheRotation();
    }

    /**
     * Build a curved rigid line. The body frame starts axis-aligned; local
     * points are stored as offsets from the length-weighted centre of mass.
     */
    public void configurePolyline(double[] wx, double[] wy, int n,
                                  double radius, double density, boolean fixed) {
        this.radius = radius;
        this.fixed = fixed;
        if (n < 2) n = 2;
        double total = 0;
        double cx = 0, cy = 0;
        for (int i = 0; i < n - 1; i++) {
            double len = Math.hypot(wx[i + 1] - wx[i], wy[i + 1] - wy[i]);
            total += len;
            cx += (wx[i] + wx[i + 1]) * 0.5 * len;
            cy += (wy[i] + wy[i + 1]) * 0.5 * len;
        }
        if (total < 1e-9) {
            total = Math.hypot(wx[n - 1] - wx[0], wy[n - 1] - wy[0]);
            cx = (wx[0] + wx[n - 1]) * 0.5;
            cy = (wy[0] + wy[n - 1]) * 0.5;
        }
        if (total < 1e-9) total = 1e-9;
        cx /= total;
        cy /= total;
        x = cx;
        y = cy;
        angle = 0;
        totalLength = total;
        halfLen = total * 0.5;
        pointCount = n;
        localX = new double[n];
        localY = new double[n];
        double bound = 0;
        for (int i = 0; i < n; i++) {
            localX[i] = wx[i] - x;
            localY[i] = wy[i] - y;
            double d = Math.hypot(localX[i], localY[i]);
            if (d > bound) bound = d;
        }
        boundRadius = bound + radius;
        mass = Math.max(1e-6, density * total);

        double inertiaSum = 0;
        for (int i = 0; i < n - 1; i++) {
            double len = Math.hypot(wx[i + 1] - wx[i], wy[i + 1] - wy[i]);
            double mi = density * len;
            double mx = (wx[i] + wx[i + 1]) * 0.5 - x;
            double my = (wy[i] + wy[i + 1]) * 0.5 - y;
            inertiaSum += mi * (len * len / 12.0 + mx * mx + my * my
                    + radius * radius * 0.25);
        }
        inertia = Math.max(1e-9, inertiaSum);
        if (fixed) {
            invMass = 0;
            invInertia = 0;
        } else {
            invMass = 1.0 / mass;
            invInertia = 1.0 / inertia;
        }
        cacheRotation();
    }

    public void refreshMass() {
        if (!fixed && mass > 1e-9) {
            double length = totalLength > 0 ? totalLength : halfLen * 2.0;
            double width = radius * 2.0;
            invMass = 1.0 / mass;
            // For a straight capsule this is exact; curved lines already set
            // their polyline inertia in configurePolyline().
            if (pointCount <= 2) {
                inertia = mass * (length * length + width * width) / 12.0;
                invInertia = 1.0 / inertia;
            } else {
                if (inertia <= 1e-9) inertia = mass * length * length / 12.0;
                invInertia = 1.0 / inertia;
            }
        } else {
            invMass = 0.0;
            invInertia = 0.0;
            if (inertia <= 0.0) inertia = mass > 0 ? mass * halfLen * halfLen / 3.0 : 0.0;
        }
    }

    public void cacheRotation() {
        c = Math.cos(angle);
        s = Math.sin(angle);
    }

    public double c = 1.0, s = 0.0;

    public double px(int i) { return x + c * localX[i] - s * localY[i]; }
    public double py(int i) { return y + s * localX[i] + c * localY[i]; }
    public int segCount() { return Math.max(1, pointCount - 1); }
    public double segAx(int i) { return px(i); }
    public double segAy(int i) { return py(i); }
    public double segBx(int i) { return px(i + 1); }
    public double segBy(int i) { return py(i + 1); }

    public double ax() { return px(0); }
    public double ay() { return py(0); }
    public double bx() { return px(pointCount - 1); }
    public double by() { return py(pointCount - 1); }

    public void setPinLocalEndA(double worldX, double worldY) {
        setPinLocal(localX[0], localY[0], worldX, worldY);
    }

    public void setPinLocalEndB(double worldX, double worldY) {
        setPinLocal(localX[pointCount - 1], localY[pointCount - 1], worldX, worldY);
    }

    public void setPinLocal(double lx, double ly, double worldX, double worldY) {
        pinned = true;
        pinLocalX = lx;
        pinLocalY = ly;
        pinX = worldX;
        pinY = worldY;
    }

    public void integrate(double dt, double gravity) {
        if (fixed || sleeping || kinematic) return;
        vy += gravity * dt;
        double damp = 1.0 - 0.025 * dt;
        vx *= damp;
        vy *= damp;
        omega *= (1.0 - 0.18 * dt);
        x += vx * dt;
        y += vy * dt;
        angle += omega * dt;
        if (angle > Math.PI) angle -= Math.PI * 2.0;
        if (angle < -Math.PI) angle += Math.PI * 2.0;
        cacheRotation();
    }

    public void solvePin() {
        if (fixed || sleeping || !pinned) return;
        double cs = Math.cos(angle), sn = Math.sin(angle);
        double rx = cs * pinLocalX - sn * pinLocalY;
        double ry = sn * pinLocalX + cs * pinLocalY;
        double ex = x + rx - pinX;
        double ey = y + ry - pinY;
        x -= ex;
        y -= ey;

        for (int it = 0; it < 2; it++) {
            double vxp = vx - omega * ry;
            double vyp = vy + omega * rx;
            double k11 = invMass + invInertia * ry * ry;
            double k12 = -invInertia * rx * ry;
            double k22 = invMass + invInertia * rx * rx;
            double det = k11 * k22 - k12 * k12;
            if (det < 1e-12) break;
            double lx = (-vxp * k22 + vyp * k12) / det;
            double ly = (-vyp * k11 + vxp * k12) / det;
            vx += invMass * lx;
            vy += invMass * ly;
            omega += invInertia * (rx * ly - ry * lx);
        }
    }

    public double pointVx(double rx, double ry) { return sleeping ? 0 : vx - omega * ry; }
    public double pointVy(double rx, double ry) { return sleeping ? 0 : vy + omega * rx; }

    public void applyImpulse(double ix, double iy, double rx, double ry) {
        if (fixed || sleeping) return;
        vx += ix * invMass;
        vy += iy * invMass;
        omega += invInertia * (rx * iy - ry * ix);
    }

    public void applyPositionCorrection(double dx, double dy) {
        if (fixed || sleeping) return;
        x += dx;
        y += dy;
    }

    public double length() { return totalLength; }

    public boolean moving() {
        return !fixed && (Math.abs(vx) + Math.abs(vy) + Math.abs(omega) * boundRadius > 0.03);
    }
}
