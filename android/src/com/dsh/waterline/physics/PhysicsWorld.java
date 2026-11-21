package com.dsh.waterline.physics;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Compact 2D engine for water circles and capsule / curved-polyline rigid
 * bodies. Lines and dynamic mechanisms share the same body class.
 */
public final class PhysicsWorld {
    public static final double GRAVITY = 9.8;
    public static final double AIR_DRAG_N = 0.0025;
    public static final double WATER_RESTITUTION = 0.04;
    public static final double WATER_FRICTION = 0.0125;

    public final double width, height;
    /** Enabled after the last droplet is released: kills micro-jitter. */
    public boolean calmMode = false;
    public final ArrayList<Drop> drops = new ArrayList<>();
    public final ArrayList<Bar> bars = new ArrayList<>();

    private double cp1x, cp1y, cp2x, cp2y;

    // uniform grid broad-phase for the 255 water particles
    private final double cellSize = 0.034;
    private int gridCols, gridRows;
    private int[] gridHead, gridNext;

    public PhysicsWorld(double width, double height) {
        this.width = width;
        this.height = height;
        gridCols = Math.max(1, (int) Math.ceil(width / cellSize));
        gridRows = Math.max(1, (int) Math.ceil(height / cellSize));
        gridHead = new int[gridCols * gridRows];
        gridNext = new int[128];
    }

    public void addDrop(Drop d) { drops.add(d); }
    public void addBar(Bar b) { bars.add(b); }

    /** New water / new line means formerly sleeping bodies must interact again. */
    public void wakeAll() {
        for (int i = 0; i < drops.size(); i++) {
            Drop d = drops.get(i);
            if (!d.removed && !d.ghost) { d.sleeping = false; d.restTime = 0; }
        }
        for (int i = 0; i < bars.size(); i++) {
            Bar b = bars.get(i);
            b.sleeping = false; b.restTime = 0;
        }
    }

    public void step(double dt, int iterations) {
        if (dt <= 0) return;
        dt = Math.min(dt, 1.0 / 120.0);

        for (int i = 0; i < bars.size(); i++) {
            Bar b = bars.get(i);
            b.integrate(dt, GRAVITY);
            if (calmMode && !b.fixed) {
                double metric = Math.abs(b.vx) + Math.abs(b.vy)
                        + Math.abs(b.omega) * b.boundRadius;
                if (metric < 0.22) {
                    b.vx *= 0.94;
                    b.vy *= 0.94;
                    b.omega *= 0.94;
                }
            }
            b.solvePin();
        }

        for (int i = 0; i < drops.size(); i++) {
            Drop d = drops.get(i);
            if (d.removed || d.ghost || d.sleeping) continue;
            d.vy += GRAVITY * dt;
            double speed = Math.hypot(d.vx, d.vy);
            if (calmMode) {
                if (speed < 0.35) {
                    d.vx *= 0.72;
                    d.vy *= 0.50;
                } else if (speed < 0.80) {
                    d.vx *= 0.90;
                    d.vy *= 0.90;
                }
            }
            if (speed > 1e-7) {
                double dv = (AIR_DRAG_N / Drop.MASS) * dt;
                if (dv > speed) dv = speed;
                d.vx -= d.vx / speed * dv;
                d.vy -= d.vy / speed * dv;
            }
            d.x += d.vx * dt;
            d.y += d.vy * dt;
            d.angle += d.spin * dt;
        }

        for (int it = 0; it < iterations; it++) {
            for (int i = 0; i < bars.size(); i++) {
                Bar a = bars.get(i);
                for (int j = i + 1; j < bars.size(); j++) {
                    Bar b = bars.get(j);
                    if (a.fixed && b.fixed) continue;
                    if (supportLinked(a, b)) continue;
                    if ((a.noGround && b.ground) || (b.noGround && a.ground)) continue;
                    if (Math.hypot(a.x - b.x, a.y - b.y)
                            > a.boundRadius + b.boundRadius + 0.01) continue;
                    collideBars(a, b);
                }
            }
            for (int i = 0; i < drops.size(); i++) {
                Drop d = drops.get(i);
                if (d.removed || d.ghost || d.sleeping) continue;
                for (int j = 0; j < bars.size(); j++) {
                    collideDropBar(d, bars.get(j));
                }
            }
            collideWaterGrid();
            for (int i = 0; i < drops.size(); i++) {
                Drop d = drops.get(i);
                if (!d.removed && !d.ghost && !d.sleeping) collideEdges(d);
            }
            for (int i = 0; i < bars.size(); i++) bars.get(i).solvePin();
        }

        if (calmMode) {
            // Bodies that stay below a small speed for 0.35 s go to sleep.
            // A sleeping body is truly static and therefore counts as still;
            // water and poison droplets are included in this same sweep.
            for (int i = 0; i < drops.size(); i++) {
                Drop d = drops.get(i);
                if (d.removed || d.ghost || d.collected || d.sleeping) continue;
                if (Math.hypot(d.vx, d.vy) < 0.060) {
                    d.restTime += dt;
                    if (d.restTime > 0.35) {
                        d.sleeping = true;
                        d.vx = 0; d.vy = 0;
                    }
                } else {
                    d.restTime = 0;
                }
            }
            for (int i = 0; i < bars.size(); i++) {
                Bar b = bars.get(i);
                if (b.fixed || !b.calmEligible || b.sleeping) continue;
                double metric = Math.abs(b.vx) + Math.abs(b.vy)
                        + Math.abs(b.omega) * b.boundRadius;
                if (metric < 0.060) {
                    b.restTime += dt;
                    if (b.restTime > 0.35) {
                        b.sleeping = true;
                        b.vx = 0; b.vy = 0; b.omega = 0;
                    }
                } else {
                    b.restTime = 0;
                }
            }
        }

        for (int i = drops.size() - 1; i >= 0; i--) {
            Drop d = drops.get(i);
            if (d.removed || d.ghost) continue;
            if (d.y > height + 0.35 || d.x < -0.4 || d.x > width + 0.4) d.removed = true;
        }
    }

    private void collideWaterGrid() {
        int n = drops.size();
        if (gridNext.length < n) gridNext = new int[Math.max(128, n * 2)];
        Arrays.fill(gridHead, -1);
        for (int i = 0; i < n; i++) {
            Drop d = drops.get(i);
            if (d.removed || d.ghost || d.sleeping) continue;
            int c = gridIndex(d.x, d.y);
            gridNext[i] = gridHead[c];
            gridHead[c] = i;
        }
        for (int i = 0; i < n; i++) {
            Drop a = drops.get(i);
            if (a.removed || a.ghost || a.sleeping) continue;
            int cx = cellX(a.x), cy = cellY(a.y);
            for (int oy = -1; oy <= 1; oy++) {
                int yy = cy + oy;
                if (yy < 0 || yy >= gridRows) continue;
                for (int ox = -1; ox <= 1; ox++) {
                    int xx = cx + ox;
                    if (xx < 0 || xx >= gridCols) continue;
                    int c = yy * gridCols + xx;
                    for (int j = gridHead[c]; j >= 0; j = gridNext[j]) {
                        if (j <= i) continue;
                        Drop b = drops.get(j);
                        if (b.removed || b.ghost || b.sleeping) continue;
                        collideDrops(a, b);
                    }
                }
            }
        }
    }

    private int cellX(double x) {
        int v = (int) Math.floor(x / cellSize);
        return v < 0 ? 0 : (v >= gridCols ? gridCols - 1 : v);
    }

    private int cellY(double y) {
        int v = (int) Math.floor(y / cellSize);
        return v < 0 ? 0 : (v >= gridRows ? gridRows - 1 : v);
    }

    private int gridIndex(double x, double y) {
        return cellY(y) * gridCols + cellX(x);
    }

    private boolean supportLinked(Bar a, Bar b) {
        if (a.supportId >= 0 && (b.ignoreSupportA == a.supportId || b.ignoreSupportB == a.supportId)) return true;
        if (b.supportId >= 0 && (a.ignoreSupportA == b.supportId || a.ignoreSupportB == b.supportId)) return true;
        return false;
    }

    private void collideDrops(Drop a, Drop b) {
        double dx = b.x - a.x, dy = b.y - a.y;
        double rr = a.radius + b.radius;
        double d2 = dx * dx + dy * dy;
        if (d2 >= rr * rr) return;
        if (d2 < 1e-12) { a.x -= 0.0002; b.x += 0.0002; return; }
        double d = Math.sqrt(d2);
        double nx = dx / d, ny = dy / d;
        double pen = rr - d;
        double corr = pen * 0.55;
        a.x -= nx * corr * 0.5; a.y -= ny * corr * 0.5;
        b.x += nx * corr * 0.5; b.y += ny * corr * 0.5;

        double rvx = b.vx - a.vx, rvy = b.vy - a.vy;
        double vn = rvx * nx + rvy * ny;
        if (vn < 0) {
            double j = -(1.0 + 0.03) * vn / (Drop.INV_MASS * 2.0);
            a.vx -= j * nx * Drop.INV_MASS;
            a.vy -= j * ny * Drop.INV_MASS;
            b.vx += j * nx * Drop.INV_MASS;
            b.vy += j * ny * Drop.INV_MASS;
            double tx = -ny, ty = nx;
            double vt = (b.vx - a.vx) * tx + (b.vy - a.vy) * ty;
            double jt = -vt / (Drop.INV_MASS * 2.0);
            double max = Math.abs(j) * 0.06;
            if (jt > max) jt = max;
            if (jt < -max) jt = -max;
            a.vx -= jt * tx * Drop.INV_MASS;
            a.vy -= jt * ty * Drop.INV_MASS;
            b.vx += jt * tx * Drop.INV_MASS;
            b.vy += jt * ty * Drop.INV_MASS;
        }
    }

    private boolean oneWayBlocks(Bar gate, double x, double y) {
        if (!gate.oneWay) return true;
        if (gate.oneWayArc) {
            boolean inside = arcInside(gate, x, y);
            return gate.oneWayBlockInside ? inside : !inside;
        }
        return (x - gate.x) * gate.oneWayNx
                + (y - gate.y) * gate.oneWayNy >= 0;
    }

    /** True when the point is on the concave (inside) side of an arc. */
    private boolean arcInside(Bar gate, double x, double y) {
        double best = Double.MAX_VALUE;
        double qx = 0, qy = 0, tx = 1, ty = 0;
        for (int i = 0; i < gate.segCount(); i++) {
            double ax = gate.segAx(i), ay = gate.segAy(i);
            double bx = gate.segBx(i), by = gate.segBy(i);
            double abx = bx - ax, aby = by - ay;
            double len2 = abx * abx + aby * aby;
            double t = len2 > 1e-12
                    ? ((x - ax) * abx + (y - ay) * aby) / len2 : 0;
            t = Math.max(0, Math.min(1, t));
            double px = ax + abx * t, py = ay + aby * t;
            double d = Math.hypot(x - px, y - py);
            if (d < best) {
                best = d; qx = px; qy = py;
                double len = Math.max(1e-9, Math.hypot(abx, aby));
                tx = abx / len; ty = aby / len;
            }
        }
        double nx = -ty, ny = tx;
        if (nx * gate.oneWayNx + ny * gate.oneWayNy < 0) { nx = -nx; ny = -ny; }
        return (x - qx) * nx + (y - qy) * ny >= 0;
    }

    private void collideDropBar(Drop d, Bar b) {
        if (b.inactive) return;
        if (!oneWayBlocks(b, d.x, d.y)) return;
        if (b.waterPass || b.sleeping) return;
        double bestPen = -1;
        int bestSeg = -1;
        double bestCx = 0, bestCy = 0, bestDx = 0, bestDy = 0, bestDist = 0;
        for (int i = 0; i < b.segCount(); i++) {
            closestPointOnSegment(d.x, d.y, b.segAx(i), b.segAy(i), b.segBx(i), b.segBy(i));
            double dx = d.x - cp1x, dy = d.y - cp1y;
            double dist = Math.hypot(dx, dy);
            double pen = d.radius + b.radius - dist;
            if (pen > bestPen) {
                bestPen = pen; bestSeg = i; bestCx = cp1x; bestCy = cp1y;
                bestDx = dx; bestDy = dy; bestDist = dist;
            }
        }
        if (bestSeg < 0 || bestPen <= 0) return;
        double nx, ny;
        if (bestDist < 1e-7) {
            double lx = b.segBx(bestSeg) - b.segAx(bestSeg);
            double ly = b.segBy(bestSeg) - b.segAy(bestSeg);
            nx = -ly; ny = lx;
            double len = Math.hypot(nx, ny);
            if (len < 1e-9) { nx = 0; ny = -1; } else { nx /= len; ny /= len; }
            if ((d.x - b.x) * nx + (d.y - b.y) * ny < 0) { nx = -nx; ny = -ny; }
        } else {
            nx = bestDx / bestDist; ny = bestDy / bestDist;
        }
        double totalInv = Drop.INV_MASS + b.invMass;
        if (totalInv <= 0) return;
        double corr = bestPen * 0.75 / totalInv;
        d.x += nx * corr * Drop.INV_MASS;
        d.y += ny * corr * Drop.INV_MASS;
        if (!b.fixed) {
            b.x -= nx * corr * b.invMass;
            b.y -= ny * corr * b.invMass;
        }

        double rx = bestCx - b.x, ry = bestCy - b.y;
        double vpx = d.vx - b.pointVx(rx, ry);
        double vpy = d.vy - b.pointVy(rx, ry);
        double vn = vpx * nx + vpy * ny;
        if (vn >= 0) return;

        double rn = rx * ny - ry * nx;
        double k = Drop.INV_MASS + b.invMass + b.invInertia * rn * rn;
        if (k <= 1e-12) return;
        double e = b.bouncy ? b.restitution
                : Math.min(WATER_RESTITUTION, b.restitution);
        double j = -(1.0 + e) * vn / k;
        d.vx += j * nx * Drop.INV_MASS;
        d.vy += j * ny * Drop.INV_MASS;
        b.applyImpulse(-j * nx, -j * ny, rx, ry);

        double tx = -ny, ty = nx;
        double vt = (d.vx - b.pointVx(rx, ry)) * tx + (d.vy - b.pointVy(rx, ry)) * ty;
        double rt = rx * ty - ry * tx;
        double kt = Drop.INV_MASS + b.invMass + b.invInertia * rt * rt;
        double jt = -vt / Math.max(kt, 1e-12);
        double mu = (WATER_FRICTION + b.friction) * 0.5;
        double max = Math.abs(j) * mu;
        if (jt > max) jt = max;
        if (jt < -max) jt = -max;
        d.vx += jt * tx * Drop.INV_MASS;
        d.vy += jt * ty * Drop.INV_MASS;
        b.applyImpulse(-jt * tx, -jt * ty, rx, ry);
    }

    private void collideBars(Bar a, Bar b) {
        if (a.sleeping || b.sleeping) return;
        for (int si = 0; si < a.segCount(); si++) {
            for (int sj = 0; sj < b.segCount(); sj++) {
                collideBarSegments(a, si, b, sj);
            }
        }
    }

    private void collideBarSegments(Bar a, int si, Bar b, int sj) {
        if (a.inactive || b.inactive) return;
        if (a.oneWay && !oneWayBlocks(a, b.x, b.y)) return;
        if (b.oneWay && !oneWayBlocks(b, a.x, a.y)) return;
        double aax = a.segAx(si), aay = a.segAy(si), abx = a.segBx(si), aby = a.segBy(si);
        double bax = b.segAx(sj), bay = b.segAy(sj), bbx = b.segBx(sj), bby = b.segBy(sj);
        closestSegmentSegment(aax, aay, abx, aby, bax, bay, bbx, bby);
        double nx = cp2x - cp1x, ny = cp2y - cp1y;
        double d2 = nx * nx + ny * ny;
        double rr = a.radius + b.radius;
        if (d2 >= rr * rr) return;
        double d = Math.sqrt(d2);
        if (d < 1e-7) {
            double dx = abx - aax, dy = aby - aay;
            nx = -dy; ny = dx;
            double len = Math.hypot(nx, ny);
            if (len < 1e-9) { nx = 0; ny = -1; } else { nx /= len; ny /= len; }
            if ((b.x - a.x) * nx + (b.y - a.y) * ny < 0) { nx = -nx; ny = -ny; }
            d = 1e-7;
        } else {
            nx /= d; ny /= d;
        }
        double pen = rr - d;
        double invSum = a.invMass + b.invMass;
        if (invSum <= 0) return;
        double px = (cp1x + cp2x) * 0.5, py = (cp1y + cp2y) * 0.5;
        double corr = pen * 0.68 / invSum;
        a.x -= nx * corr * a.invMass;
        a.y -= ny * corr * a.invMass;
        b.x += nx * corr * b.invMass;
        b.y += ny * corr * b.invMass;

        double rax = px - a.x, ray = py - a.y;
        double rbx = px - b.x, rby = py - b.y;
        double rvx = b.pointVx(rbx, rby) - a.pointVx(rax, ray);
        double rvy = b.pointVy(rbx, rby) - a.pointVy(rax, ray);
        double vn = rvx * nx + rvy * ny;
        if (vn >= 0) return;
        double ran = rax * ny - ray * nx;
        double rbn = rbx * ny - rby * nx;
        double k = a.invMass + b.invMass + a.invInertia * ran * ran + b.invInertia * rbn * rbn;
        if (k <= 1e-12) return;
        double e = (a.bouncy || b.bouncy)
                ? Math.max(a.restitution, b.restitution)
                : Math.min(a.restitution, b.restitution);
        double j = -(1.0 + e) * vn / k;
        a.applyImpulse(-j * nx, -j * ny, rax, ray);
        b.applyImpulse(j * nx, j * ny, rbx, rby);

        double tx = -ny, ty = nx;
        double vt = (b.pointVx(rbx, rby) - a.pointVx(rax, ray)) * tx
                + (b.pointVy(rbx, rby) - a.pointVy(rax, ray)) * ty;
        double rat = rax * ty - ray * tx;
        double rbt = rbx * ty - rby * tx;
        double kt = a.invMass + b.invMass + a.invInertia * rat * rat + b.invInertia * rbt * rbt;
        double jt = -vt / Math.max(kt, 1e-12);
        double mu = (a.friction + b.friction) * 0.5;
        double max = Math.abs(j) * mu;
        if (jt > max) jt = max;
        if (jt < -max) jt = -max;
        a.applyImpulse(-jt * tx, -jt * ty, rax, ray);
        b.applyImpulse(jt * tx, jt * ty, rbx, rby);
    }

    private void collideEdges(Drop d) {
        double r = d.radius;
        if (d.x < r) {
            d.x = r;
            if (d.vx < 0) d.vx = -d.vx * 0.22;
            d.vy *= 0.995;
        } else if (d.x > width - r) {
            d.x = width - r;
            if (d.vx > 0) d.vx = -d.vx * 0.22;
            d.vy *= 0.995;
        }
        if (d.y < r) {
            d.y = r;
            if (d.vy < 0) d.vy = -d.vy * 0.15;
        } else if (d.y > height - r) {
            d.y = height - r;
            if (d.vy > 0) d.vy = -d.vy * 0.12;
            d.vx *= 0.96;
        }
    }

    private void closestPointOnSegment(double px, double py,
                                       double ax, double ay, double bx, double by) {
        double abx = bx - ax, aby = by - ay;
        double len2 = abx * abx + aby * aby;
        double t = len2 > 1e-12 ? ((px - ax) * abx + (py - ay) * aby) / len2 : 0.0;
        if (t < 0) t = 0; else if (t > 1) t = 1;
        cp1x = ax + abx * t;
        cp1y = ay + aby * t;
    }

    private void closestSegmentSegment(double ax, double ay, double bx, double by,
                                       double cx, double cy, double dx, double dy) {
        double d1x = bx - ax, d1y = by - ay;
        double d2x = dx - cx, d2y = dy - cy;
        double rx = ax - cx, ry = ay - cy;
        double a = d1x * d1x + d1y * d1y;
        double e = d2x * d2x + d2y * d2y;
        double f = d2x * rx + d2y * ry;
        double s, t;
        final double EPS = 1e-12;
        if (a <= EPS && e <= EPS) {
            s = t = 0;
        } else if (a <= EPS) {
            s = 0; t = clamp01(f / e);
        } else {
            double c = d1x * rx + d1y * ry;
            if (e <= EPS) {
                t = 0; s = clamp01(-c / a);
            } else {
                double b = d1x * d2x + d1y * d2y;
                double denom = a * e - b * b;
                if (denom != 0) s = clamp01((b * f - c * e) / denom);
                else s = 0;
                t = (b * s + f) / e;
                if (t < 0) {
                    t = 0; s = clamp01(-c / a);
                } else if (t > 1) {
                    t = 1; s = clamp01((b - c) / a);
                }
            }
        }
        cp1x = ax + d1x * s;
        cp1y = ay + d1y * s;
        cp2x = cx + d2x * t;
        cp2y = cy + d2y * t;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
