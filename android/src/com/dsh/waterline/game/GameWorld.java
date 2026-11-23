package com.dsh.waterline.game;

import com.dsh.waterline.physics.Bar;
import com.dsh.waterline.physics.Drop;
import com.dsh.waterline.physics.PhysicsWorld;

import java.util.ArrayList;
import java.util.Random;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Locale;

/** Simulation for the user-created level. */
public final class GameWorld {
    public enum State { READY, RUNNING, WON, LOST }

    public static final int TOTAL_DROPS = 225;
    public static final int CUP_FULL_DROPS = 200; // 100% visual capacity
    public static final int TARGET_DROPS = 190;   // 95% required to win
    public static final double LINE_DENSITY_KG_PER_M = 5.0;
    public static final double LINE_RADIUS = 0.0065;
    public static final double LINE_MAX_LEN = 0.605;
    public static final double LINE_MIN_LEN = 0.045;
    public static final double SNAP_RADIUS = 0.036;
    public static final double DROP_RADIUS = 0.0155 * Math.sqrt(0.75) * (2.0 / 3.0);
    public static final double FAUCET_SPOUT_Y = 0.165;
    public static final int MAX_LINES = 3;
    public static final double ENTER_FADE_SECONDS = 1.5;
    public static final double STILL_SECONDS = 5.0;

    public Level level;
    public PhysicsWorld physics;
    public final ArrayList<GameLine> lines = new ArrayList<>();
    public final ArrayList<Bar> supportBars = new ArrayList<>();
    public final ArrayList<Bar> fireBars = new ArrayList<>();
    public final ArrayList<Bar> mudBars = new ArrayList<>();
    public final ArrayList<CircleState> bubbles = new ArrayList<>();
    public final ArrayList<CircleState> poisonBalls = new ArrayList<>();
    public final ArrayList<PoisonSource> poisonSources = new ArrayList<>();
    public final ArrayList<Steam> steams = new ArrayList<>();
    public final ArrayList<BallState> solidBalls = new ArrayList<>();
    public final ArrayList<BallState> iceBalls = new ArrayList<>();
    public final ArrayList<Level.CircleObj> purifiers = new ArrayList<>();
    public final ArrayList<BreakableState> breakables = new ArrayList<>();
    public final ArrayList<BlinkState> blinkers = new ArrayList<>();
    public final ArrayList<Mover> movers = new ArrayList<>();
    public final ArrayList<TriggerState> triggers = new ArrayList<>();
    public final ArrayList<Rotation> rotations = new ArrayList<>();
    public final ArrayList<Translation> translations = new ArrayList<>();
    public final Map<Object, Bar> barByObject = new IdentityHashMap<>();
    public Bar fireBall;
    public Bar cupBody;
    public double cupLocalInnerLeft, cupLocalInnerRight, cupLocalTop, cupLocalBottom;
    public State state = State.READY;
    public double pixelsPerMeter = 1800.0;
    public String failReason = "";

    public int spawned = 0;
    public int dropsInCup = 0;
    public int poisonDropsInCup = 0;
    public int maxDropsInCup = 0;
    public double time = 0.0;
    public double runTime = 0.0;
    public double sinceLastSpawn = 0.0;
    public double stillTimer = 0.0;
    public double cupOutTimer = 0.0;
    public double completeTimer = 0.0;
    public double manualEndTimer = 0.0;
    public boolean manualEndRequested = false;
    public String endReason = "";
    public double elapsedAtWin = 0.0;
    public int linesAtWin = 0;
    /** Length accounting survives clearLines(); first line is the free base. */
    public double firstLineLength = -1.0;
    public double totalLineLength = 0.0;
    public String toast = "";
    public double toastTimer = 0.0;

    private double accumulator = 0.0;
    private double motionClock = 0.0;
    private boolean allSpawnedFlag = false;

    public static final class Snap {
        public int index = -1;
        public double x, y, dist = Double.MAX_VALUE;
    }

    public static final class GameLine {
        public final Bar bar;
        public final boolean pinA, pinB;
        public final double pinAx, pinAy, pinBx, pinBy;
        public final double length, mass;
        public final int seed;
        public GameLine(Bar bar, boolean pinA, boolean pinB,
                        double pinAx, double pinAy, double pinBx, double pinBy,
                        double length, int seed) {
            this.bar = bar; this.pinA = pinA; this.pinB = pinB;
            this.pinAx = pinAx; this.pinAy = pinAy; this.pinBx = pinBx; this.pinBy = pinBy;
            this.length = length; this.mass = bar.mass; this.seed = seed;
        }
        public String massLabel() { return String.format(Locale.US, "%.2f kg", mass); }
    }

    public static final class CircleState {
        public final Level.CircleObj def;
        public boolean used;
        public CircleState(Level.CircleObj def) { this.def = def; }
    }

    public static final class PoisonSource {
        public final Level.PoisonFaucet def;
        public int remaining;
        public double timer;
        public PoisonSource(Level.PoisonFaucet def) {
            this.def = def; this.remaining = def.count;
        }
    }

    public static final class BallState {
        public final Bar bar;
        public final Level.CircleObj def;
        public BallState(Bar bar, Level.CircleObj def) { this.bar = bar; this.def = def; }
    }

    public static final class TriggerState {
        public final Level.RotateTrigger def;
        public final Object target;
        public final Bar bar;
        public double cooldown;
        public int remaining;
        public TriggerState(Level.RotateTrigger def, Object target, Bar bar) {
            this.def = def; this.target = target; this.bar = bar;
            this.remaining = def.count;
        }
    }

    public static final class Rotation {
        public final Bar bar;
        public final Object target;
        public final TriggerState trigger;
        public double remainingDeg;
        public final double speedDeg;
        public final double pivotX, pivotY;
        public final boolean fixedPivot;
        public Rotation(Bar bar, Object target, TriggerState trigger,
                        double remainingDeg, double speedDeg,
                        double pivotX, double pivotY, boolean fixedPivot) {
            this.bar = bar; this.target = target; this.trigger = trigger;
            this.remainingDeg = remainingDeg; this.speedDeg = speedDeg;
            this.pivotX = pivotX; this.pivotY = pivotY; this.fixedPivot = fixedPivot;
        }
    }

    public static final class Translation {
        public final Bar bar;
        public final Object target;
        public double remaining;
        public final double dirX, dirY, speed;
        public Translation(Bar bar, Object target, double remaining,
                           double dirX, double dirY, double speed) {
            this.bar = bar; this.target = target; this.remaining = remaining;
            this.dirX = dirX; this.dirY = dirY; this.speed = speed;
        }
    }

    public static final class BlinkState {
        public final Bar bar;
        public final Object def;
        public final Level.Motion motion;
        public BlinkState(Bar bar, Object def, Level.Motion motion) {
            this.bar = bar; this.def = def; this.motion = motion;
        }
    }

    public static final class BreakableState {
        public final Bar bar;
        public final Object def;
        public final boolean support;
        public boolean burning;
        public double burnTime;
        public double burnSpread;
        public double spreadTimer;
        public double burnX, burnY;
        public BreakableState(Bar bar, Object def, boolean support) {
            this.bar = bar; this.def = def; this.support = support;
        }
    }

    public static final class Steam {
        public double x, y, life, maxLife, size;
        public Steam(double x, double y, double life, double size) {
            this.x = x; this.y = y; this.life = life; this.maxLife = life; this.size = size;
        }
    }

    public static final class Mover {
        public final Bar bar;
        public final Level.CircleObj circle;
        public final Level.SegObj seg;
        public final Level.Support support;
        public final Level.Motion motion;
        public final double angle0;
        public final double baseAngle;
        public Mover(Bar bar, Level.CircleObj circle, Level.SegObj seg,
                     Level.Support support, Level.Motion motion, double angle0) {
            this.bar = bar; this.circle = circle; this.seg = seg;
            this.support = support; this.motion = motion; this.angle0 = angle0;
            this.baseAngle = bar == null ? 0 : bar.angle;
        }
    }

    public final long seed;
    private final Random rng;

    public GameWorld(Level level) {
        this(level, new Random().nextLong());
    }

    public GameWorld(Level level, long seed) {
        this.level = level;
        this.seed = seed;
        this.rng = new Random(seed);
        load();
    }

    public void load() {
        physics = new PhysicsWorld(Level.WORLD_W, Level.WORLD_H);
        lines.clear(); supportBars.clear(); fireBars.clear(); mudBars.clear();
        bubbles.clear(); poisonBalls.clear(); poisonSources.clear(); steams.clear(); movers.clear();
        solidBalls.clear(); iceBalls.clear(); purifiers.clear(); breakables.clear(); blinkers.clear();
        triggers.clear(); rotations.clear(); translations.clear(); barByObject.clear();
        fireBall = null; cupBody = null;
        motionClock = 0.0;
        state = State.READY;
        failReason = "";
        spawned = 0; dropsInCup = 0; poisonDropsInCup = 0; maxDropsInCup = 0;
        time = 0; runTime = 0; sinceLastSpawn = 0; stillTimer = 0;
        firstLineLength = -1.0; totalLineLength = 0.0;
        cupOutTimer = 0; completeTimer = 0; manualEndTimer = 0; manualEndRequested = false;
        endReason = "";
        accumulator = 0; allSpawnedFlag = false;
        toast = ""; toastTimer = 0;
        buildWorld();
    }

    private void buildWorld() {
        Bar ground = Bar.capsule(-0.2, 0.955, 0.8, 0.955, 0.011, 0, true);
        ground.tag = Tags.GROUND;
        ground.ground = true;
        physics.addBar(ground);

        // The cup is one dynamic U-shaped rigid body. Water already counted in
        // the cup stays counted even if the cup is pushed or tipped.
        double wall = Level.CUP_WALL;
        double left = level.cupLeft(), right = level.cupRight();
        double rim = level.cupRimY(), bottom = level.cupBottomY;
        double lx = left + wall * 0.5, rx = right - wall * 0.5;
        double by = bottom - wall * 0.5;
        cupBody = Bar.polyline(new double[]{lx, lx, rx, rx},
                new double[]{rim, by, by, rim}, 4, wall * 0.5, 4.6, false);
        cupBody.tag = Tags.CUP;
        cupBody.restitution = 0.10;
        cupBody.friction = 0.55;
        physics.addBar(cupBody);
        cupLocalInnerLeft = level.cupInnerLeft() - cupBody.x;
        cupLocalInnerRight = level.cupInnerRight() - cupBody.x;
        cupLocalTop = level.cupRimY() - cupBody.y;
        cupLocalBottom = level.cupInnerBottom() - cupBody.y;

        Bar pipe = Bar.capsule(level.faucetX, 0.015, level.faucetX, 0.150, 0.020, 0, true);
        pipe.tag = Tags.FAUCET;
        physics.addBar(pipe);
        Bar collar = Bar.capsule(level.faucetX - 0.032, 0.092, level.faucetX + 0.032, 0.092,
                0.011, 0, true);
        collar.tag = Tags.FAUCET;
        physics.addBar(collar);

        for (int i = 0; i < level.supports.size(); i++) {
            Level.Support s = level.supports.get(i);
            int n = 11;
            double[] xs = new double[n], ys = new double[n];
            for (int k = 0; k < n; k++) {
                double t = k / (double) (n - 1);
                xs[k] = s.px(t); ys[k] = s.py(t);
            }
            Bar b = Bar.polyline(xs, ys, n, 0.0048, 1.0, s.motion == null);
            if (s.kind == Level.ARC_SUPPORT) s.kind = Level.ARC_WALL;
            b.tag = s.kind == Level.ARC_FIRE ? Tags.FIRE_LINE
                    : s.kind == Level.ARC_WALL ? Tags.WALL
                    : s.kind == Level.ARC_BOUNCE ? Tags.BOUNCE
                    : s.kind == Level.ARC_BREAKABLE ? Tags.BREAKABLE
                    : (s.kind == Level.ARC_ONE_WAY_IN || s.kind == Level.ARC_ONE_WAY_OUT)
                    ? Tags.ONE_WAY : Tags.SUPPORT;
            b.bouncy = s.kind == Level.ARC_BOUNCE;
            b.breakable = s.kind == Level.ARC_BREAKABLE;
            b.oneWay = s.kind == Level.ARC_ONE_WAY_IN || s.kind == Level.ARC_ONE_WAY_OUT;
            if (b.oneWay) {
                b.oneWayArc = true;
                b.oneWayBlockInside = s.kind == Level.ARC_ONE_WAY_IN;
                double dx = s.x2 - s.x1, dy = s.y2 - s.y1;
                double dl = Math.max(1e-6, Math.hypot(dx, dy));
                double cross = dx * (s.ym - s.y1) - dy * (s.xm - s.x1);
                double sign = cross >= 0 ? -1 : 1;
                b.oneWayNx = -dy / dl * sign;
                b.oneWayNy = dx / dl * sign;
            }
            b.restitution = b.bouncy ? 0.92 : 0.30;
            b.friction = b.bouncy ? 0.10 : 0.32;
            b.supportId = i;
            if (b.breakable) breakables.add(new BreakableState(b, s, true));
            if (s.motion != null) {
                makeKinematic(b, s.motion, s.centerX(), s.centerY());
                movers.add(new Mover(b, null, null, s, s.motion, 0));
            }
            physics.addBar(b);
            supportBars.add(b);
            barByObject.put(s, b);
            if (s.kind == Level.ARC_FIRE) fireBars.add(b);
            if (s.motion != null && s.motion.blink) blinkers.add(new BlinkState(b, s, s.motion));
        }

        for (Level.SegObj s : level.segments) {
            double radius = s.kind == Level.SEG_FIRE_LINE ? 0.0055
                    : s.kind == Level.SEG_ONE_WAY ? 0.0055 : 0.0065;
            Bar b = Bar.capsule(s.x1, s.y1, s.x2, s.y2, radius, 0, true);
            b.tag = s.kind == Level.SEG_FIRE_LINE ? Tags.FIRE_LINE
                    : s.kind == Level.SEG_BOUNCE ? Tags.BOUNCE
                    : s.kind == Level.SEG_ONE_WAY ? Tags.ONE_WAY
                    : s.kind == Level.SEG_BREAKABLE ? Tags.BREAKABLE : Tags.WALL;
            b.antiSqueeze = s.kind == Level.SEG_WALL;
            b.oneWay = s.kind == Level.SEG_ONE_WAY;
            if (b.oneWay) {
                b.oneWayNx = -Math.sin(b.angle);
                b.oneWayNy = Math.cos(b.angle);
            }
            b.bouncy = s.kind == Level.SEG_BOUNCE;
            b.breakable = s.kind == Level.SEG_BREAKABLE;
            b.restitution = b.bouncy ? 0.92
                    : s.kind == Level.SEG_FIRE_LINE ? 0.28 : 0.12;
            b.friction = b.bouncy ? 0.10 : 0.35;
            if (b.breakable) breakables.add(new BreakableState(b, s, false));
            if (s.motion != null) {
                makeKinematic(b, s.motion, s.centerX(), s.centerY());
                movers.add(new Mover(b, null, s, null, s.motion,
                        orbit0At(s.centerX(), s.centerY(), s.motion)));
            }
            physics.addBar(b);
            barByObject.put(s, b);
            if (s.kind == Level.SEG_FIRE_LINE) fireBars.add(b);
            if (s.motion != null && s.motion.blink) blinkers.add(new BlinkState(b, s, s.motion));
        }

        for (Level.CircleObj o : level.circles) {
            if (o.kind == Level.CIRCLE_PURIFIER) {
                purifiers.add(o);
                if (o.motion != null) movers.add(new Mover(null, o, null, null, o.motion, orbit0(o)));
                if (o.motion != null && o.motion.blink) blinkers.add(new BlinkState(null, o, o.motion));
            } else if (o.kind == Level.CIRCLE_ICE) {
                double mass = Math.max(0.30, 500.0 * Math.PI * o.radius * o.radius);
                Bar b = Bar.capsule(o.cx, o.cy, o.cx + 0.002, o.cy, o.radius, mass, false);
                b.tag = Tags.ICE;
                b.circleShape = true;
                b.restitution = 0.12;
                b.friction = 0.30;
                o.motion = null;
                physics.addBar(b);
                barByObject.put(o, b);
                iceBalls.add(new BallState(b, o));
            } else if (o.kind == Level.CIRCLE_BUBBLE) {
                bubbles.add(new CircleState(o));
                if (o.motion != null) movers.add(new Mover(null, o, null, null, o.motion, orbit0(o)));
                if (o.motion != null && o.motion.blink) blinkers.add(new BlinkState(null, o, o.motion));
            } else if (o.kind == Level.CIRCLE_POISON_BALL) {
                poisonBalls.add(new CircleState(o));
                if (o.motion != null) movers.add(new Mover(null, o, null, null, o.motion, orbit0(o)));
                if (o.motion != null && o.motion.blink) blinkers.add(new BlinkState(null, o, o.motion));
            } else if (o.kind == Level.CIRCLE_BALL) {
                double mass = Math.max(0.25, 500.0 * Math.PI * o.radius * o.radius);
                Bar b = Bar.capsule(o.cx, o.cy, o.cx + 0.002, o.cy, o.radius, mass, false);
                b.tag = Tags.BALL;
                b.circleShape = true;
                b.restitution = 0.18;
                b.friction = 0.40;
                // Dynamic circles (physics=1) cannot be path-driven.
                o.motion = null;
                physics.addBar(b);
                barByObject.put(o, b);
                solidBalls.add(new BallState(b, o));
            } else if (o.kind == Level.CIRCLE_FIRE_BALL) {
                double mass = Math.max(0.35, 2.1 * o.radius * o.radius / 0.0025);
                Bar b = Bar.capsule(o.cx, o.cy, o.cx + 0.002, o.cy, o.radius, mass, false);
                b.tag = Tags.FIRE_BALL;
                b.restitution = 0.24;
                b.friction = 0.45;
                b.noGround = true;
                // Dynamic circles (physics=1) cannot be path-driven.
                o.motion = null;
                physics.addBar(b);
                barByObject.put(o, b);
                fireBars.add(b);
                fireBall = b;
            } else {
                Bar b = Bar.capsule(o.cx, o.cy, o.cx + 0.002, o.cy, o.radius,
                        o.motion != null ? 1 : 0, o.motion == null);
                b.tag = o.kind == Level.CIRCLE_FIRE_RING ? Tags.FIRE_RING : Tags.WALL;
                b.restitution = o.kind == Level.CIRCLE_FIRE_RING ? 0.30 : 0.12;
                b.friction = 0.32;
                if (o.motion != null) {
                    makeKinematic(b, o.motion, o.cx, o.cy);
                    movers.add(new Mover(b, o, null, null, o.motion, orbit0(o)));
                }
                physics.addBar(b);
                barByObject.put(o, b);
                if (o.kind == Level.CIRCLE_FIRE_RING) fireBars.add(b);
                if (o.motion != null && o.motion.blink) blinkers.add(new BlinkState(b, o, o.motion));
            }
        }

        for (Level.RotateTrigger rt : level.triggers) {
            Object target = null;
            if (rt.targetType == 0 && rt.targetIndex >= 0
                    && rt.targetIndex < level.segments.size()) {
                target = level.segments.get(rt.targetIndex);
            } else if (rt.targetType == 1 && rt.targetIndex >= 0
                    && rt.targetIndex < level.supports.size()) {
                target = level.supports.get(rt.targetIndex);
            } else if (rt.targetType == 2 && rt.targetIndex >= 0
                    && rt.targetIndex < level.circles.size()) {
                target = level.circles.get(rt.targetIndex);
            }
            if (target != null) triggers.add(new TriggerState(rt, target, barByObject.get(target)));
        }

        for (Level.PoisonFaucet p : level.poisonFaucets) {
            p.y = Level.POISON_FAUCET_Y;
            poisonSources.add(new PoisonSource(p));
        }
    }

    private void makeKinematic(Bar b, Level.Motion m, double currentX, double currentY) {
        b.kinematic = true;
        b.fixed = false;
        b.pinned = false;
        b.sleeping = false;
        b.calmEligible = false;
        b.invMass = 0;
        b.invInertia = 0;
        b.vx = 0; b.vy = 0; b.omega = 0;
        if (m.type == Level.MOVE_LINEAR) {
            b.x = m.x1; b.y = m.y1;
        } else if (m.type == Level.MOVE_PATH) {
            if (m.points.size() > 0) { b.x = m.points.get(0).x; b.y = m.points.get(0).y; }
        } else if (m.type == Level.MOVE_SPIN) {
            b.x = m.x1; b.y = m.y1;
        } else {
            b.x = m.x1 + m.radius;
            b.y = m.y1;
            // keep the editor position if it already lies on the orbit
            double dx = currentX - m.x1, dy = currentY - m.y1;
            double d = Math.hypot(dx, dy);
            if (d > 1e-5) { b.x = currentX; b.y = currentY; }
        }
    }

    private double orbit0(Level.CircleObj o) {
        if (o.motion == null) return 0;
        return orbit0At(o.cx, o.cy, o.motion);
    }

    private double orbit0At(double cx, double cy, Level.Motion m) {
        if (m.type != Level.MOVE_ORBIT) return 0;
        return Math.atan2(cy - m.y1, cx - m.x1);
    }

    private void updateMovers() {
        for (Mover mv : movers) {
            Level.Motion m = mv.motion;
            double px, py, vx, vy;
            if (m.type == Level.MOVE_LINEAR) {
                double dx = m.x2 - m.x1, dy = m.y2 - m.y1;
                double len = Math.hypot(dx, dy);
                if (len < 1e-6 || m.speed <= 0) { px = m.x1; py = m.y1; vx = vy = 0; }
                else {
                    double travel = motionClock * m.speed;
                    double cycle = travel % (2 * len);
                    if (cycle <= len) {
                        px = m.x1 + dx * cycle / len;
                        py = m.y1 + dy * cycle / len;
                        vx = dx / len * m.speed;
                        vy = dy / len * m.speed;
                    } else {
                        double back = cycle - len;
                        px = m.x2 - dx * back / len;
                        py = m.y2 - dy * back / len;
                        vx = -dx / len * m.speed;
                        vy = -dy / len * m.speed;
                    }
                }
            } else if (m.type == Level.MOVE_PATH) {
                double[] q = pathPosition(m, motionClock * m.speed);
                px = q[0]; py = q[1];
                vx = q[2] * m.speed; vy = q[3] * m.speed;
            } else if (m.type == Level.MOVE_SPIN) {
                px = m.x1; py = m.y1; vx = 0; vy = 0;
            } else {
                double r = Math.max(0.005, m.radius);
                double ang = mv.angle0 + motionClock * (m.speed / r);
                px = m.x1 + Math.cos(ang) * r;
                py = m.y1 + Math.sin(ang) * r;
                vx = -Math.sin(ang) * m.speed;
                vy = Math.cos(ang) * m.speed;
            }
            double a = mv.baseAngle + m.spin * motionClock;
            if (mv.bar != null) {
                mv.bar.prevX = mv.bar.x;
                mv.bar.prevY = mv.bar.y;
                mv.bar.prevAngle = mv.bar.angle;
                mv.bar.x = px; mv.bar.y = py;
                mv.bar.vx = vx; mv.bar.vy = vy;
                double oldA = mv.bar.angle;
                mv.bar.angle = a;
                if (mv.bar.oneWay) {
                    double da = a - oldA;
                    double c = Math.cos(da), sn = Math.sin(da);
                    double nx = mv.bar.oneWayNx * c - mv.bar.oneWayNy * sn;
                    double ny = mv.bar.oneWayNx * sn + mv.bar.oneWayNy * c;
                    mv.bar.oneWayNx = nx; mv.bar.oneWayNy = ny;
                }
                mv.bar.cacheRotation();
                if (mv.seg != null) {
                    double c = Math.cos(a), s = Math.sin(a);
                    double hl = mv.bar.halfLen;
                    mv.seg.x1 = px - c * hl; mv.seg.y1 = py - s * hl;
                    mv.seg.x2 = px + c * hl; mv.seg.y2 = py + s * hl;
                }
            }
            if (mv.circle != null) { mv.circle.cx = px; mv.circle.cy = py; }
        }
    }

    /** Position + unit tangent at travelled distance along a polyline path. */
    private double[] pathPosition(Level.Motion m, double travel) {
        int n = m.points.size();
        if (n == 0) return new double[]{m.x1, m.y1, 0, 0};
        if (n == 1) return new double[]{m.points.get(0).x, m.points.get(0).y, 0, 0};
        double total = 0;
        for (int i = 0; i < n - 1; i++) {
            total += Math.hypot(m.points.get(i + 1).x - m.points.get(i).x,
                    m.points.get(i + 1).y - m.points.get(i).y);
        }
        if (m.loop) {
            total += Math.hypot(m.points.get(0).x - m.points.get(n - 1).x,
                    m.points.get(0).y - m.points.get(n - 1).y);
        }
        if (total < 1e-6 || m.speed <= 0) {
            return new double[]{m.points.get(0).x, m.points.get(0).y, 0, 0};
        }
        double cycle;
        double dirSign = 1;
        if (m.loop) {
            cycle = travel % total;
        } else {
            double t = travel % (2 * total);
            if (t <= total) cycle = t;
            else { cycle = 2 * total - t; dirSign = -1; }
        }
        int segments = m.loop ? n : n - 1;
        for (int i = 0; i < segments; i++) {
            Level.Pt a = m.points.get(i);
            Level.Pt b = m.points.get((i + 1) % n);
            double dx = b.x - a.x, dy = b.y - a.y;
            double len = Math.hypot(dx, dy);
            if (len < 1e-9) continue;
            if (cycle <= len) {
                double f = cycle / len;
                return new double[]{a.x + dx * f, a.y + dy * f,
                        dx / len * dirSign, dy / len * dirSign};
            }
            cycle -= len;
        }
        Level.Pt a = m.points.get(0);
        return new double[]{a.x, a.y, 0, 0};
    }

    private void syncSolidBalls() {
        for (BallState b : solidBalls) {
            b.def.cx = b.bar.x;
            b.def.cy = b.bar.y;
        }
        for (BallState b : iceBalls) {
            b.def.cx = b.bar.x;
            b.def.cy = b.bar.y;
        }
    }

    public Snap nearestSupportPoint(double x, double y) {
        Snap best = new Snap();
        for (int i = 0; i < level.supports.size(); i++) {
            Level.Support s = level.supports.get(i);
            double prevX = s.px(0), prevY = s.py(0);
            for (int k = 1; k <= 16; k++) {
                double t = k / 16.0;
                double nx = s.px(t), ny = s.py(t);
                double abx = nx - prevX, aby = ny - prevY;
                double len2 = abx * abx + aby * aby;
                double u = len2 > 1e-12
                        ? ((x - prevX) * abx + (y - prevY) * aby) / len2 : 0;
                u = Math.max(0, Math.min(1, u));
                double qx = prevX + abx * u, qy = prevY + aby * u;
                double d = Math.hypot(x - qx, y - qy);
                if (d < best.dist) { best.dist = d; best.index = i; best.x = qx; best.y = qy; }
                prevX = nx; prevY = ny;
            }
        }
        return best;
    }

    public GameLine addLine(double x1, double y1, double x2, double y2) {
        return addLinePath(new double[]{x1, x2}, new double[]{y1, y2}, 2);
    }

    public GameLine addLinePath(double[] rawX, double[] rawY, int rawN) {
        if (lines.size() >= MAX_LINES) { showToast("最多 3 根线，先点「清线」"); return null; }
        if (rawN < 2) return null;
        double[] wx = new double[Math.min(40, rawN)], wy = new double[wx.length];
        int n = 0;
        for (int i = 0; i < rawN; i++) {
            if (i > 0 && Math.hypot(rawX[i] - wx[n - 1], rawY[i] - wy[n - 1]) < 0.004) continue;
            if (n >= wx.length) break;
            wx[n] = rawX[i]; wy[n] = rawY[i]; n++;
        }
        if (n < 2) { showToast("线太短了"); return null; }
        if (n > 26) {
            double[] nx = new double[26], ny = new double[26];
            for (int i = 0; i < 26; i++) {
                double t = i / 25.0 * (n - 1);
                int j = (int) Math.floor(t), k = Math.min(n - 1, j + 1);
                double f = t - j;
                nx[i] = wx[j] * (1 - f) + wx[k] * f;
                ny[i] = wy[j] * (1 - f) + wy[k] * f;
            }
            wx = nx; wy = ny; n = 26;
        }
        double len = 0;
        for (int i = 0; i < n - 1; i++) len += Math.hypot(wx[i + 1] - wx[i], wy[i + 1] - wy[i]);
        if (len < LINE_MIN_LEN) { showToast("线太短了"); return null; }
        if (len > LINE_MAX_LEN) { showToast("线太长：最长 60.5 cm"); return null; }

        if (firstLineLength < 0) firstLineLength = len;
        totalLineLength += len;

        Bar bar = Bar.polyline(wx, wy, n, LINE_RADIUS, LINE_DENSITY_KG_PER_M, false);
        bar.restitution = 0.10;
        bar.friction = 0.22;
        bar.tag = Tags.LINE;
        physics.addBar(bar);
        physics.wakeAll();
        GameLine gl = new GameLine(bar, false, false, 0, 0, 0, 0,
                len, 11 + lines.size() * 29 + level.name.hashCode() % 97);
        lines.add(gl);
        showToast("自由曲线 · 会掉落，可搭在弧挡板上");
        return gl;
    }

    public void clearLines() {
        for (GameLine gl : lines) physics.bars.remove(gl.bar);
        lines.clear();
        showToast("线已清空");
    }

    public void startWater() {
        if (state != State.READY) return;
        state = State.RUNNING;
        runTime = 0; sinceLastSpawn = 0;
        showToast("水龙头打开：225 颗水珠");
    }

    public void requestManualEnd() {
        if (state != State.RUNNING || manualEndRequested) return;
        manualEndRequested = true;
        manualEndTimer = 5.0;
        showToast("手动结束：5 秒后结算");
    }

    public void update(double dt) {
        if (dt > 0.05) dt = 0.05;
        time += dt;
        if (toastTimer > 0) toastTimer = Math.max(0, toastTimer - dt);
        // Drawing / editor phase: absolutely no physics.
        if (state == State.READY) return;
        if (state == State.WON || state == State.LOST) {
            stepPhysics(dt);
            updateCollection(dt);
            syncSolidBalls();
            return;
        }

        motionClock += dt;
        updateMovers();
        updateBlinkers();
        updateTriggers(dt);
        applyDropForces(dt);
        accumulator += dt;
        final double FIXED = 1.0 / 240.0;
        int guard = 0;
        while (accumulator >= FIXED && guard < 12) {
            physics.step(FIXED, 2);
            accumulator -= FIXED;
            guard++;
        }

        runTime += dt;
        sinceLastSpawn += dt;
        while (spawned < TOTAL_DROPS && sinceLastSpawn >= 0.020) {
            spawnWaterDrop(level.faucetX + (rng.nextDouble() - 0.5) * 0.003,
                    FAUCET_SPOUT_Y + 0.004, false, 0, 0.15);
            sinceLastSpawn -= 0.020;
        }
        if (spawned >= TOTAL_DROPS && !allSpawnedFlag) {
            allSpawnedFlag = true;
            for (Bar b : physics.bars) b.calmEligible = true;
        }
        physics.calmMode = false;
        updateSpecialMechanisms(dt);
        updateHazards(dt);
        updateCollection(dt);
        syncSolidBalls();
        if (state != State.RUNNING) return;

        // 1) Cup left the screen: 5 s grace, then lose.
        if (cupOffscreen()) {
            cupOutTimer += dt;
            if (cupOutTimer >= 5.0) {
                finish(false, "杯子离开屏幕 5 秒");
                return;
            }
        } else cupOutTimer = 0;

        // 2) Manual end: fixed 5 s countdown.
        if (manualEndRequested) {
            manualEndTimer -= dt;
            if (manualEndTimer <= 0) {
                if (dropsInCup >= TARGET_DROPS) finish(true, "手动结束 · 杯量达到 95%");
                else finish(false, "手动结束：只装到 " + (dropsInCup / 2) + "%，需达到 95%");
                return;
            }
        }

        // 3) The level already reached 95%: force settlement after 15 s even
        // if some body is still moving (anti-stuck fallback).
        if (dropsInCup >= TARGET_DROPS) {
            completeTimer += dt;
            if (completeTimer >= 15.0) {
                finish(true, "关卡已完成 · 15 秒结束");
                return;
            }
        } else completeTimer = 0;

        if (allSpawnedFlag) {
            // Normal-speed physics until the stillness countdown starts.
            if (everythingStill()) {
                stillTimer += dt;
                physics.calmMode = true;
            } else {
                stillTimer = 0;
                physics.calmMode = false;
            }
            if (stillTimer >= STILL_SECONDS) {
                if (dropsInCup >= TARGET_DROPS) {
                    finish(true, "杯量达到 95% · 所有物体静止");
                } else {
                    finish(false, "只装到 " + (dropsInCup / 2) + "%，需达到 95%");
                }
            }
        }
    }

    private void finish(boolean win, String reason) {
        state = win ? State.WON : State.LOST;
        endReason = reason;
        if (!win) failReason = reason;
        elapsedAtWin = runTime;
        linesAtWin = lines.size();
        physics.calmMode = false;
        showToast(reason);
    }

    public boolean cupOffscreen() {
        if (cupBody == null) return false;
        return cupBody.x < -0.02 || cupBody.x > Level.WORLD_W + 0.02
                || cupBody.y < -0.02 || cupBody.y > Level.WORLD_H + 0.02;
    }

    public boolean hasEndingCountdown() {
        return manualEndRequested || cupOutTimer > 0 || completeTimer > 0
                || (allSpawnedFlag && stillTimer > 0);
    }

    public String endingStatus() {
        if (manualEndRequested) return String.format(Locale.US, "手动结束 %.1f / 5.0 s", manualEndTimer);
        if (cupOutTimer > 0) return String.format(Locale.US, "杯子离开屏幕 %.1f / 5.0 s", cupOutTimer);
        if (completeTimer > 0) return String.format(Locale.US, "95%% 已完成 · %.1f / 15.0 s", completeTimer);
        if (allSpawnedFlag) return String.format(Locale.US, "所有物体静止 %.1f / 5.0 s", stillTimer);
        return "";
    }

    private void stepPhysics(double dt) {
        applyDropForces(dt);
        accumulator += dt;
        final double FIXED = 1.0 / 240.0;
        int guard = 0;
        while (accumulator >= FIXED && guard < 12) {
            physics.step(FIXED, 2);
            accumulator -= FIXED;
            guard++;
        }
    }

    private void spawnWaterDrop(double x, double y, boolean poison, double vx, double vy) {
        Drop d = new Drop(x, y, DROP_RADIUS, spawned);
        d.angle = rng.nextDouble() * Math.PI * 2;
        d.spin = (rng.nextDouble() - 0.5) * 2.0;
        d.vx = vx; d.vy = vy; d.poison = poison;
        physics.addDrop(d);
        spawned++;
    }

    private void spawnReleasedDrop(double x, double y, boolean poison) {
        spawnReleasedDrop(x, y, poison, false);
    }

    private void spawnReleasedDrop(double x, double y, boolean poison, boolean secondary) {
        double a = rng.nextDouble() * Math.PI * 2;
        double sp = 0.25 + rng.nextDouble() * 0.55;
        Drop d = new Drop(x + Math.cos(a) * DROP_RADIUS, y + Math.sin(a) * DROP_RADIUS,
                DROP_RADIUS, spawned);
        d.angle = rng.nextDouble() * Math.PI * 2;
        d.spin = (rng.nextDouble() - 0.5) * 2.0;
        d.vx = Math.cos(a) * sp;
        d.vy = Math.sin(a) * sp;
        d.poison = poison;
        d.secondaryRelease = secondary;
        physics.addDrop(d);
        physics.wakeAll();
        // released droplets do not belong to the faucet's 225 count
    }

    private void spawnPoisonDrop(double x, double y) {
        Drop d = new Drop(x, y + DROP_RADIUS, DROP_RADIUS, spawned);
        d.angle = rng.nextDouble() * Math.PI * 2;
        d.spin = (rng.nextDouble() - 0.5) * 2.0;
        d.vx = 0.0;
        d.vy = 0.55 + rng.nextDouble() * 0.20;
        d.horizForce = (rng.nextDouble() * 2.0 - 1.0) * 0.15;
        d.horizForceTime = 0.05 + rng.nextDouble() * 0.05;
        d.poison = true;
        physics.addDrop(d);
        physics.wakeAll();
    }

    private void updateTriggers(double dt) {
        // Apply queued rotations and translations first.
        for (int i = rotations.size() - 1; i >= 0; i--) {
            Rotation r = rotations.get(i);
            double step = Math.min(Math.abs(r.remainingDeg), Math.abs(r.speedDeg) * dt);
            if (step <= 1e-9) { rotations.remove(i); continue; }
            double sgn = Math.signum(r.remainingDeg);
            double deltaRad = Math.toRadians(step * sgn);
            Bar b = r.bar;
            if (r.fixedPivot) {
                double dx = b.x - r.pivotX, dy = b.y - r.pivotY;
                double c = Math.cos(deltaRad), sn = Math.sin(deltaRad);
                b.x = r.pivotX + dx * c - dy * sn;
                b.y = r.pivotY + dx * sn + dy * c;
            }
            double oldAngle = b.angle;
            b.angle += deltaRad;
            b.cacheRotation();
            if (b.oneWay) {
                double c = Math.cos(deltaRad), sn = Math.sin(deltaRad);
                double nx = b.oneWayNx * c - b.oneWayNy * sn;
                double ny = b.oneWayNx * sn + b.oneWayNy * c;
                b.oneWayNx = nx; b.oneWayNy = ny;
            }
            syncTarget(r.bar, r.target);
            if (r.fixedPivot && r.trigger != null) {
                r.trigger.def.offsetX = r.pivotX - b.x;
                r.trigger.def.offsetY = r.pivotY - b.y;
            }
            r.remainingDeg -= step * sgn;
            if (Math.abs(r.remainingDeg) <= 1e-9) rotations.remove(i);
        }
        for (int i = translations.size() - 1; i >= 0; i--) {
            Translation t = translations.get(i);
            double step = Math.min(Math.abs(t.remaining), Math.abs(t.speed) * dt);
            if (step <= 1e-9) { translations.remove(i); continue; }
            t.bar.x += t.dirX * step;
            t.bar.y += t.dirY * step;
            syncTarget(t.bar, t.target);
            t.remaining -= step;
            if (Math.abs(t.remaining) <= 1e-9) translations.remove(i);
        }
        for (TriggerState ts : triggers) {
            ts.cooldown = Math.max(0, ts.cooldown - dt);
            if (ts.def.count > 0 && ts.remaining <= 0) continue;
            if (ts.cooldown > 0) continue;
            double cx, cy;
            if (ts.bar != null) { cx = ts.bar.x; cy = ts.bar.y; }
            else if (ts.target instanceof Level.CircleObj) {
                cx = ((Level.CircleObj) ts.target).cx; cy = ((Level.CircleObj) ts.target).cy;
            } else if (ts.target instanceof Level.SegObj) {
                cx = ((Level.SegObj) ts.target).centerX(); cy = ((Level.SegObj) ts.target).centerY();
            } else if (ts.target instanceof Level.Support) {
                cx = ((Level.Support) ts.target).centerX(); cy = ((Level.Support) ts.target).centerY();
            } else continue;
            double tx = cx + ts.def.offsetX, ty = cy + ts.def.offsetY;
            boolean hit = false;
            for (Drop d : physics.drops) {
                if (d.removed || d.ghost) continue;
                if (Math.hypot(d.x - tx, d.y - ty) < d.radius + 0.012) {
                    hit = true; break;
                }
            }
            if (!hit) continue;
            if (ts.def.count > 0) ts.remaining--;
            ts.cooldown = 0.25;
            if (ts.bar != null) {
                if (ts.def.mode == 1) {
                    double sx = ts.bar.x + ts.def.offsetX;
                    double sy = ts.bar.y + ts.def.offsetY;
                    double dx, dy, dist;
                    if (ts.def.hasEndpoint) {
                        double ex = ts.def.endpointRelative ? sx + ts.def.endX : ts.def.endX;
                        double ey = ts.def.endpointRelative ? sy + ts.def.endY : ts.def.endY;
                        dx = ex - sx; dy = ey - sy;
                        dist = Math.hypot(dx, dy);
                    } else {
                        double rad = Math.toRadians(ts.def.angleDeg);
                        dx = Math.cos(rad); dy = Math.sin(rad);
                        dist = ts.def.distance;
                    }
                    if (dist > 1e-6) {
                        translations.add(new Translation(ts.bar, ts.target, dist,
                                dx / Math.max(1e-9, Math.hypot(dx, dy)),
                                dy / Math.max(1e-9, Math.hypot(dx, dy)),
                                ts.def.moveSpeed));
                    }
                } else {
                    boolean fixed = ts.def.mode == 2;
                    double px = ts.bar.x + ts.def.offsetX;
                    double py = ts.bar.y + ts.def.offsetY;
                    boolean merged = false;
                    for (Rotation r : rotations) {
                        if (r.bar == ts.bar && r.fixedPivot == fixed
                                && (!fixed || (Math.abs(r.pivotX - px) < 1e-6
                                && Math.abs(r.pivotY - py) < 1e-6))) {
                            r.remainingDeg += ts.def.angleDeg;
                            merged = true; break;
                        }
                    }
                    if (!merged) rotations.add(new Rotation(ts.bar, ts.target, ts,
                            ts.def.angleDeg, ts.def.speedDeg, px, py, fixed));
                }
            }
        }
    }

    private void syncTarget(Bar b, Object target) {
        if (target instanceof Level.SegObj) {
            Level.SegObj sg = (Level.SegObj) target;
            double c = Math.cos(b.angle), sn = Math.sin(b.angle);
            sg.x1 = b.x - c * b.halfLen; sg.y1 = b.y - sn * b.halfLen;
            sg.x2 = b.x + c * b.halfLen; sg.y2 = b.y + sn * b.halfLen;
        } else if (target instanceof Level.CircleObj) {
            Level.CircleObj o = (Level.CircleObj) target;
            o.cx = b.x; o.cy = b.y;
        }
    }

    private void applyDropForces(double dt) {
        for (Drop d : physics.drops) {
            if (d.removed || d.horizForceTime <= 0) continue;
            double step = Math.min(dt, d.horizForceTime);
            d.vx += d.horizForce / Drop.MASS * step;
            d.horizForceTime -= step;
        }
    }

    private void markTriggeredLines(Level.CircleObj c) {
        for (Bar b : physics.bars) {
            if (b.tag != Tags.LINE || b.inactive) continue;
            if (barCircleDistance(b, c.cx, c.cy) < c.radius + b.radius) {
                b.bubbleTriggered = true;
            }
        }
    }

    private void markTriggeredDrops(Level.CircleObj c) {
        for (Drop d : physics.drops) {
            if (d.removed || d.ghost || d.secondaryRelease) continue;
            if (Math.hypot(d.x - c.cx, d.y - c.cy) < c.radius + d.radius) {
                d.bubbleTriggered = true;
            }
        }
    }

    private void updateHazards(double dt) {
        // Poison faucets emit one small poison droplet at a time.
        for (PoisonSource ps : poisonSources) {
            if (ps.remaining <= 0) continue;
            ps.timer += dt;
            while (ps.timer >= 0.055 && ps.remaining > 0) {
                ps.timer -= 0.055;
                spawnPoisonDrop(ps.def.x, ps.def.y);
                ps.remaining--;
            }
        }

        // Fire evaporates water on contact.
        for (int i = physics.drops.size() - 1; i >= 0; i--) {
            Drop d = physics.drops.get(i);
            if (d.removed || d.ghost) continue;
            boolean burn = false;
            for (Bar b : fireBars) {
                if (b.inactive) continue;
                // Swept radius catches fast-moving fire lines, especially when
                // they move upward through falling droplets between frames.
                double sweep = Math.hypot(b.vx, b.vy) * 0.035;
                if (circleBarDistance(b, d.x, d.y) < d.radius + b.radius + sweep) {
                    burn = true; break;
                }
            }
            if (burn) {
                d.removed = true;
                addSteam(d.x, d.y, d.radius);
            }
        }

        // Bubble / poison balls trigger when touched by a gravity object.
        for (CircleState cs : bubbles) {
            if (!cs.used && !cs.def.hidden && touched(cs.def)) {
                cs.used = true;
                markTriggeredDrops(cs.def);
                markTriggeredLines(cs.def);
                int n = releasedCount(cs.def.radius, 0.0005) * 2;
                for (int k = 0; k < n; k++) spawnReleasedDrop(cs.def.cx, cs.def.cy, false, true);
                addSteam(cs.def.cx, cs.def.cy, 0.03);
            }
        }
        for (CircleState cs : poisonBalls) {
            if (!cs.used && !cs.def.hidden && touched(cs.def)) {
                cs.used = true;
                markTriggeredDrops(cs.def);
                markTriggeredLines(cs.def);
                int n = releasedCount(cs.def.radius, 0.0010) * 2;
                for (int k = 0; k < n; k++) spawnReleasedDrop(cs.def.cx, cs.def.cy, true, true);
                addSteam(cs.def.cx, cs.def.cy, 0.02);
            }
        }

        // Poison water + normal water => fixed mud bead.
        for (int i = 0; i < physics.drops.size(); i++) {
            Drop p = physics.drops.get(i);
            if (p.removed || p.ghost || !p.poison) continue;
            for (int j = 0; j < physics.drops.size(); j++) {
                Drop w = physics.drops.get(j);
                if (w == p || w.removed || w.ghost || w.poison) continue;
                if (Math.hypot(p.x - w.x, p.y - w.y) < p.radius + w.radius) {
                    double mx = (p.x + w.x) * 0.5, my = (p.y + w.y) * 0.5;
                    p.removed = true; w.removed = true;
                    addMud(mx, my);
                    break;
                }
            }
        }

        // Fireball falling into the cup is an instant loss.
        if (fireBall != null && !fireBall.fixed) {
            if (insideCup(fireBall.x, fireBall.y)) {
                state = State.LOST;
                failReason = "火球落入杯中";
                showToast("火球入杯 · 挑战失败");
                return;
            }
            if (fireBall.y > 1.5 || fireBall.x < -0.5 || fireBall.x > 1.1) {
                physics.bars.remove(fireBall);
                fireBars.remove(fireBall);
                fireBall = null;
            }
        }
        for (int i = steams.size() - 1; i >= 0; i--) {
            Steam s = steams.get(i);
            s.life -= dt;
            s.y -= dt * 0.05;
            if (s.life <= 0) steams.remove(i);
        }
    }

    private int releasedCount(double radius, double pct) {
        double px = radius * pixelsPerMeter;
        double area = Math.PI * px * px;
        int n = (int) Math.round(area * pct);
        return Math.max(1, Math.min(300, n));
    }

    private void addSteam(double x, double y, double size) {
        if (steams.size() > 80) steams.remove(0);
        steams.add(new Steam(x, y, 0.8, Math.max(0.012, size)));
    }

    private void addMud(double x, double y) {
        Bar m = Bar.capsule(x, y, x + 0.002, y, DROP_RADIUS * 1.15, 0, true);
        m.tag = Tags.MUD;
        m.friction = 0.7;
        physics.addBar(m);
        mudBars.add(m);
        addSteam(x, y, DROP_RADIUS);
    }

    public boolean insideCup(double x, double y) {
        if (cupBody == null) return false;
        double dx = x - cupBody.x, dy = y - cupBody.y;
        double lx = cupBody.c * dx + cupBody.s * dy;
        double ly = -cupBody.s * dx + cupBody.c * dy;
        return lx > cupLocalInnerLeft && lx < cupLocalInnerRight
                && ly > cupLocalTop && ly < cupLocalBottom + 0.01;
    }

    public void updateCollection(double dt) {
        for (int i = 0; i < physics.drops.size(); i++) {
            Drop d = physics.drops.get(i);
            if (d.removed) continue;
            if (d.collected) {
                d.fade -= dt;
                if (d.fade <= 0) d.removed = true;
                continue;
            }
            if (!d.ghost && touchesCupSurface(d)) {
                d.collected = true;
                d.ghost = true;
                d.vx = 0; d.vy = 0;
                d.fade = ENTER_FADE_SECONDS;
                if (d.poison) {
                    poisonDropsInCup++;
                    dropsInCup = Math.max(0, dropsInCup - 1);
                } else {
                    dropsInCup++;
                    if (dropsInCup > maxDropsInCup) maxDropsInCup = dropsInCup;
                }
            }
        }
    }

    /** Local Y of the current water surface in the cup body frame. */
    public double cupSurfaceLocalY() {
        double f = Math.min(1.0, dropsInCup / (double) CUP_FULL_DROPS);
        return cupLocalBottom - f * (cupLocalBottom - cupLocalTop);
    }

    /** A droplet disappears only when it actually meets the water surface. */
    private boolean touchesCupSurface(Drop d) {
        if (cupBody == null) return false;
        // Poison water cannot vanish in a dry cup: it needs a real water
        // surface to react with. Bottom of an empty cup is only the initial
        // surface for normal water droplets.
        if (d.poison && dropsInCup <= 0) return false;
        double dx = d.x - cupBody.x, dy = d.y - cupBody.y;
        double lx = cupBody.c * dx + cupBody.s * dy;
        double ly = -cupBody.s * dx + cupBody.c * dy;
        if (lx < cupLocalInnerLeft || lx > cupLocalInnerRight) return false;
        if (ly < cupLocalTop - d.radius) return false;
        if (ly > cupLocalBottom + d.radius) return false;
        double surface = cupSurfaceLocalY();
        // The lower edge of the droplet must reach the liquid surface.
        return ly + d.radius >= surface - 0.0005;
    }

    private void updateBlinkers() {
        for (BlinkState bs : blinkers) {
            Level.Motion m = bs.motion;
            double vis = Math.max(0.01, m.blinkVisibleTime);
            double hid = Math.max(0.01, m.blinkHiddenTime);
            double total = vis + hid;
            double t = motionClock % total;
            boolean hidden = m.blinkInitialVisible ? t >= vis : t < hid;
            if (bs.bar != null) bs.bar.inactive = hidden && !m.blinkCollideHidden;
            if (bs.def instanceof Level.SegObj) ((Level.SegObj) bs.def).hidden = hidden;
            else if (bs.def instanceof Level.Support) ((Level.Support) bs.def).hidden = hidden;
            else if (bs.def instanceof Level.CircleObj) ((Level.CircleObj) bs.def).hidden = hidden;
        }
    }

    private void updateSpecialMechanisms(double dt) {
        // Purifier: poison drops that enter become ordinary water.
        for (Level.CircleObj p : purifiers) {
            if (p.hidden) continue;
            for (Drop d : physics.drops) {
                if (d.removed || d.ghost || !d.poison) continue;
                if (Math.hypot(d.x - p.cx, d.y - p.cy) < p.radius + d.radius) {
                    d.poison = false;
                }
            }
        }

        // Ice bricks melt on contact with fire.
        for (int i = iceBalls.size() - 1; i >= 0; i--) {
            BallState ice = iceBalls.get(i);
            if (ice.bar.inactive) continue;
            boolean melt = false;
            for (Bar f : fireBars) {
                if (barBarDistance(ice.bar, f) < ice.bar.radius + f.radius + 0.008) {
                    melt = true; break;
                }
            }
            if (melt) {
                ice.def.melted = true;
                physics.bars.remove(ice.bar);
                iceBalls.remove(i);
                int n = Math.max(4, releasedCount(ice.def.radius, 0.0005) * 3);
                for (int k = 0; k < n; k++) {
                    spawnReleasedDrop(ice.def.cx, ice.def.cy, false);
                }
                addSteam(ice.def.cx, ice.def.cy, ice.def.radius);
            }
        }

        // Breakable wood: ignition spreads 1 cm/s, then the board burns through.
        for (int i = breakables.size() - 1; i >= 0; i--) {
            BreakableState st = breakables.get(i);
            if (!breakables.contains(st)) continue;
            Bar b = st.bar;
            if (b.inactive) continue;
            // Fire contact ignites.
            if (!st.burning) {
                for (Bar f : fireBars) {
                    if (barBarDistance(b, f) < b.radius + f.radius + 0.010) {
                        ignite(st, b.x, b.y);
                        break;
                    }
                }
            }
            if (st.burning) {
                st.burnTime += dt;
                st.burnSpread = Math.min(0.20, st.burnSpread + 0.01 * dt);
                st.spreadTimer += dt;
                setBurnVisual(st);
                if (st.spreadTimer >= 1.0) {
                    st.spreadTimer -= 1.0;
                    igniteNearby(st);
                }
                if (st.burnTime >= 1.0) {
                    breakWood(st);
                    break;
                }
            }

            // Impact: normal force above 3 N smashes; every 6 N reaches 1 cm.
            double bestF = 0;
            double cx = b.x, cy = b.y;
            for (BallState ball : solidBalls) {
                double d = barBarDistance(b, ball.bar);
                if (d < b.radius + ball.bar.radius + 0.006) {
                    double f = impactForce(b, ball.bar);
                    if (f > bestF) {
                        bestF = f;
                        cx = (b.x + ball.bar.x) * .5;
                        cy = (b.y + ball.bar.y) * .5;
                    }
                }
            }
            for (BallState ice : iceBalls) {
                double d = barBarDistance(b, ice.bar);
                if (d < b.radius + ice.bar.radius + 0.006) {
                    double f = impactForce(b, ice.bar);
                    if (f > bestF) {
                        bestF = f;
                        cx = (b.x + ice.bar.x) * .5;
                        cy = (b.y + ice.bar.y) * .5;
                    }
                }
            }
            if (bestF > 3.0) {
                int cm = Math.max(0, (int) (bestF / 6.0));
                breakWood(st);
                for (int k = breakables.size() - 1; k >= 0; k--) {
                    BreakableState other = breakables.get(k);
                    if (other == st) continue;
                    if (Math.hypot(other.bar.x - cx, other.bar.y - cy)
                            <= cm * 0.01 + 0.008) {
                        breakWood(other);
                    }
                }
            }
        }
    }

    private double impactForce(Bar wood, Bar ball) {
        double nx = -Math.sin(wood.angle), ny = Math.cos(wood.angle);
        double vn = Math.abs(ball.vx * nx + ball.vy * ny);
        return ball.mass * vn / 0.016;
    }

    private void setBurnVisual(BreakableState st) {
        if (st.def instanceof Level.SegObj) {
            Level.SegObj s = (Level.SegObj) st.def;
            s.burning = true; s.burnProgress = Math.min(1, st.burnTime);
            s.burnSpread = st.burnSpread;
            s.burnX = st.burnX; s.burnY = st.burnY;
        } else if (st.def instanceof Level.Support) {
            Level.Support s = (Level.Support) st.def;
            s.burning = true; s.burnProgress = Math.min(1, st.burnTime);
            s.burnSpread = st.burnSpread;
            s.burnX = st.burnX; s.burnY = st.burnY;
        }
    }

    private void ignite(BreakableState st, double x, double y) {
        if (st.burning) return;
        st.burning = true;
        st.burnTime = 0;
        st.burnSpread = 0;
        st.spreadTimer = 0;
        st.burnX = x;
        st.burnY = y;
        setBurnVisual(st);
    }

    private void igniteNearby(BreakableState st) {
        double ca = Math.cos(st.bar.angle), sa = Math.sin(st.bar.angle);
        for (int k = breakables.size() - 1; k >= 0; k--) {
            BreakableState other = breakables.get(k);
            if (other == st || other.burning) continue;
            double dx = other.bar.x - st.bar.x, dy = other.bar.y - st.bar.y;
            double tangent = dx * ca + dy * sa;
            double normal = -dx * sa + dy * ca;
            if (Math.abs(tangent) <= 0.015 && normal >= -0.010 && normal <= 0.020) {
                ignite(other, other.bar.x, other.bar.y);
            }
        }
    }

    private void breakWood(BreakableState st) {
        if (!breakables.contains(st)) return;
        Bar b = st.bar;
        if (st.def instanceof Level.SegObj) ((Level.SegObj) st.def).broken = true;
        if (st.def instanceof Level.Support) ((Level.Support) st.def).broken = true;
        physics.bars.remove(b);
        supportBars.remove(b);
        fireBars.remove(b);
        movers.removeIf(mv -> mv.bar == b);
        breakables.remove(st);
        addSteam(b.x, b.y, Math.min(0.08, b.boundRadius));
    }

    private double barBarDistance(Bar a, Bar b) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < a.segCount(); i++) {
            for (int j = 0; j < b.segCount(); j++) {
                best = Math.min(best, distSegSeg(
                        a.segAx(i), a.segAy(i), a.segBx(i), a.segBy(i),
                        b.segAx(j), b.segAy(j), b.segBx(j), b.segBy(j)));
            }
        }
        return best;
    }

    private double distSegSeg(double ax, double ay, double bx, double by,
                              double cx, double cy, double dx, double dy) {
        double d1 = distPointSeg(ax, ay, cx, cy, dx, dy);
        double d2 = distPointSeg(bx, by, cx, cy, dx, dy);
        double d3 = distPointSeg(cx, cy, ax, ay, bx, by);
        double d4 = distPointSeg(dx, dy, ax, ay, bx, by);
        return Math.min(Math.min(d1, d2), Math.min(d3, d4));
    }

    private boolean touched(Level.CircleObj c) {
        for (Drop d : physics.drops) {
            if (d.removed || d.ghost || d.secondaryRelease || d.bubbleTriggered) continue;
            if (Math.hypot(d.x - c.cx, d.y - c.cy) < c.radius + d.radius) return true;
        }
        for (Bar b : physics.bars) {
            if (b.inactive || b.bubbleTriggered || b.tag != Tags.LINE) continue;
            double sweep = (Math.hypot(b.x - b.prevX, b.y - b.prevY)
                    + Math.abs(b.angle - b.prevAngle) * b.boundRadius) * 0.55;
            double reach = c.radius + b.radius + sweep;
            if (barCircleDistance(b, c.cx, c.cy) < reach) return true;
            if (barCircleDistanceAt(b, c.cx, c.cy, b.prevX, b.prevY, b.prevAngle)
                    < reach) return true;
        }
        return false;
    }

    private double barCircleDistanceAt(Bar b, double cx, double cy,
                                       double px, double py, double angle) {
        double ca = Math.cos(angle), sa = Math.sin(angle);
        double best = Double.MAX_VALUE;
        for (int i = 0; i < b.segCount(); i++) {
            double ax = px + ca * b.localX[i] - sa * b.localY[i];
            double ay = py + sa * b.localX[i] + ca * b.localY[i];
            double bx = px + ca * b.localX[i + 1] - sa * b.localY[i + 1];
            double by = py + sa * b.localX[i + 1] + ca * b.localY[i + 1];
            best = Math.min(best, distPointSeg(cx, cy, ax, ay, bx, by));
        }
        return best;
    }

    private double barCircleDistance(Bar b, double cx, double cy) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < b.segCount(); i++) {
            best = Math.min(best, distPointSeg(cx, cy, b.segAx(i), b.segAy(i),
                    b.segBx(i), b.segBy(i)));
        }
        return best;
    }

    private double circleBarDistance(Bar b, double x, double y) {
        return barCircleDistance(b, x, y);
    }

    private double distPointSeg(double px, double py, double ax, double ay, double bx, double by) {
        double abx = bx - ax, aby = by - ay;
        double len2 = abx * abx + aby * aby;
        double t = len2 > 1e-12 ? ((px - ax) * abx + (py - ay) * aby) / len2 : 0;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(px - (ax + abx * t), py - (ay + aby * t));
    }

    public boolean everythingStill() {
        // Includes ordinary water, poison water and released droplets.
        for (Drop d : physics.drops) {
            if (d.removed || d.ghost) continue;
            if (!d.sleeping && Math.hypot(d.vx, d.vy) > 0.050) return false;
        }
        for (Bar b : physics.bars) {
            if (b.fixed || b.kinematic) continue;
            if (!b.sleeping && Math.abs(b.vx) + Math.abs(b.vy)
                    + Math.abs(b.omega) * b.boundRadius > 0.050) return false;
        }
        return true;
    }

    public boolean awaitingSettle() { return state == State.RUNNING && allSpawnedFlag; }
    public boolean waitingForSettle() { return awaitingSettle(); }
    public double settleProgress() { return Math.min(1.0, stillTimer / STILL_SECONDS); }

    public int lineCount() { return lines.size(); }
    /** 0.5% per droplet, 200 droplets = 100%. */
    public int progressPercent() { return Math.min(100, dropsInCup / 2); }
    public double fillFraction() { return Math.min(1.0, dropsInCup / (double) CUP_FULL_DROPS); }

    /**
     * The first line's length is free. Every additional 10% of that base
     * length adds 0.1 s. Clearing lines does not affect these totals.
     */
    public double lineTimePenalty() {
        if (firstLineLength <= 1e-9) return 0.0;
        double extraRatio = (totalLineLength - firstLineLength) / firstLineLength;
        // The first 100% of additional line material is free; beyond that,
        // every further +10% adds +0.1 s.
        return Math.max(0.0, extraRatio - 1.0);
    }

    public double finalTime() { return elapsedAtWin + lineTimePenalty(); }

    /** Additional line material as a percentage of the first line's length. */
    public int lineMaterialPercent() {
        if (firstLineLength <= 1e-9) return 0;
        double extraRatio = (totalLineLength - firstLineLength) / firstLineLength;
        return (int) Math.max(0, Math.round(extraRatio * 100.0));
    }

    public boolean isBubbleUsed(Level.CircleObj o) {
        for (CircleState s : bubbles) if (s.def == o) return s.used;
        for (CircleState s : poisonBalls) if (s.def == o) return s.used;
        return false;
    }

    public void showToast(String s) {
        toast = s;
        toastTimer = 2.6;
    }
}
