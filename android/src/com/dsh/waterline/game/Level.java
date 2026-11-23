package com.dsh.waterline.game;

import java.util.ArrayList;

/** A user-designed level. Everything here is editable and persisted as JSON. */
public final class Level {
    public static final double WORLD_W = 0.60;
    public static final double WORLD_H = 1.00;

    // Cup volume is 7/17 of the old cup; in 2D we scale both dimensions by
    // sqrt(7/17) so the visible area (volume) matches that ratio.
    public static final double CUP_VOLUME_RATIO = 7.0 / 17.0;
    public static final double CUP_LINEAR = Math.sqrt(CUP_VOLUME_RATIO);
    public static final double BASE_CUP_W = 0.222;
    public static final double BASE_CUP_H = 0.200;
    public static final double CUP_W = BASE_CUP_W * CUP_LINEAR;
    public static final double CUP_H = BASE_CUP_H * CUP_LINEAR;
    public static final double CUP_WALL = 0.0075;
    public static final double FAUCET_SPOUT_Y = 0.165;
    public static final double POISON_FAUCET_Y = 0.105;

    public static final int SEG_SUPPORT = 0;
    public static final int SEG_WALL = 1;
    public static final int SEG_FIRE_LINE = 2;
    public static final int SEG_BOUNCE = 3;
    public static final int SEG_ONE_WAY = 4;
    public static final int SEG_BREAKABLE = 5;

    public static final int CIRCLE_SOLID = 0;
    public static final int CIRCLE_FIRE_RING = 1;
    public static final int CIRCLE_FIRE_BALL = 2;
    public static final int CIRCLE_BUBBLE = 3;
    public static final int CIRCLE_POISON_BALL = 4;
    public static final int CIRCLE_BALL = 5;
    public static final int CIRCLE_PURIFIER = 6;
    public static final int CIRCLE_ICE = 7;

    public static final class Pt {
        public double x, y;
        public Pt(double x, double y) { this.x = x; this.y = y; }
    }

    public static class Seg {
        public double x1, y1, x2, y2;
        public Seg(double x1, double y1, double x2, double y2) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        }
        public double centerX() { return (x1 + x2) * 0.5; }
        public double centerY() { return (y1 + y2) * 0.5; }
        public double length() { return Math.hypot(x2 - x1, y2 - y1); }
    }

    public static final int MOVE_NONE = 0;
    public static final int MOVE_LINEAR = 1;
    public static final int MOVE_ORBIT = 2;
    public static final int MOVE_PATH = 3;
    public static final int MOVE_SPIN = 4;

    /** Kinematic path for an editor mechanism. */
    public static final class Motion {
        public int type;
        public double x1, y1, x2, y2; // linear endpoints, or orbit centre in x1/y1
        public double radius;
        public double speed; // metres/second along the path
        public double phase;
        public double spin; // radians/second around its own centre
        public boolean loop; // false: ping-pong; true: closed loop
        public boolean blink;
        public boolean blinkInitialVisible = true;
        public double blinkVisibleTime = 1.0;
        public double blinkHiddenTime = 1.0;
        public boolean blinkCollideHidden = false;
        public final ArrayList<Pt> points = new ArrayList<>();
        public Motion(int type, double x1, double y1, double x2, double y2,
                      double radius, double speed) {
            this.type = type; this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
            this.radius = radius; this.speed = speed;
        }
        public boolean hasPoints() { return points.size() >= 2; }
    }

    public static final class SegObj extends Seg {
        public int kind;
        public Motion motion;
        public boolean broken;
        public boolean hidden;
        public boolean burning;
        public double burnProgress;
        public double burnSpread;
        public double burnX, burnY;
        public SegObj(int kind, double x1, double y1, double x2, double y2) {
            super(x1, y1, x2, y2);
            this.kind = kind;
        }
        public double centerX() { return (x1 + x2) * 0.5; }
        public double centerY() { return (y1 + y2) * 0.5; }
    }

    public static final int ARC_SUPPORT = 0;
    public static final int ARC_WALL = 1;
    public static final int ARC_FIRE = 2;
    public static final int ARC_BOUNCE = 3;
    public static final int ARC_BREAKABLE = 4;
    public static final int ARC_ONE_WAY_IN = 5;
    public static final int ARC_ONE_WAY_OUT = 6;

    /** A three-point arc: start, through-point, end. */
    public static final class Support {
        public double x1, y1, xm, ym, x2, y2;
        public int kind;
        public Motion motion;
        public boolean broken;
        public boolean hidden;
        public boolean burning;
        public double burnProgress;
        public double burnSpread;
        public double burnX, burnY;
        public Support(double x1, double y1, double xm, double ym, double x2, double y2) {
            this(x1, y1, xm, ym, x2, y2, ARC_WALL);
        }
        public Support(double x1, double y1, double xm, double ym, double x2, double y2,
                       int kind) {
            this.x1 = x1; this.y1 = y1; this.xm = xm; this.ym = ym;
            this.x2 = x2; this.y2 = y2; this.kind = kind;
        }
        public double qx() { return 2 * xm - (x1 + x2) * 0.5; }
        public double qy() { return 2 * ym - (y1 + y2) * 0.5; }
        public double centerX() { return (x1 + xm + x2) / 3.0; }
        public double centerY() { return (y1 + ym + y2) / 3.0; }
        public double px(double t) {
            double u = 1 - t;
            return u * u * x1 + 2 * u * t * qx() + t * t * x2;
        }
        public double py(double t) {
            double u = 1 - t;
            return u * u * y1 + 2 * u * t * qy() + t * t * y2;
        }
    }

    public static final class CircleObj {
        public int kind;
        public double cx, cy, radius;
        public Motion motion;
        public boolean melted;
        public boolean hidden;
        public int count; // for poison faucet stored separately
        public CircleObj(int kind, double cx, double cy, double radius) {
            this.kind = kind; this.cx = cx; this.cy = cy; this.radius = radius;
        }
    }

    public static final class RotateTrigger {
        public int targetType; // 0 line, 1 arc, 2 circle
        public int targetIndex;
        public double offsetX, offsetY;
        public double angleDeg;
        public double speedDeg;
        public int count; // 0 = unlimited
        public int mode; // 0 center rotate, 1 translate, 2 fixed-point rotate
        public double distance; // metres, translation mode
        public double moveSpeed; // metres/second, translation mode
        public RotateTrigger(int targetType, int targetIndex, double offsetX, double offsetY,
                             double angleDeg, double speedDeg, int count) {
            this(targetType, targetIndex, offsetX, offsetY, angleDeg, speedDeg, count, 0, 0, 0);
        }
        public RotateTrigger(int targetType, int targetIndex, double offsetX, double offsetY,
                             double angleDeg, double speedDeg, int count,
                             int mode, double distance, double moveSpeed) {
            this.targetType = targetType; this.targetIndex = targetIndex;
            this.offsetX = offsetX; this.offsetY = offsetY;
            this.angleDeg = angleDeg; this.speedDeg = speedDeg; this.count = count;
            this.mode = mode; this.distance = distance; this.moveSpeed = moveSpeed;
        }
    }

    public static final class TextObj {
        public double x, y;
        public String text;
        public TextObj(double x, double y, String text) {
            this.x = x; this.y = y; this.text = text;
        }
    }

    public static final class PoisonFaucet {
        public double x, y;
        public int count;
        public PoisonFaucet(double x, double y, int count) {
            this.x = x; this.y = y; this.count = count;
        }
    }

    public String name = "我的关卡";
    public double faucetX = 0.30;
    public double cupCx = 0.30;
    public double cupBottomY = 0.940;
    public final ArrayList<Support> supports = new ArrayList<>();
    public final ArrayList<SegObj> segments = new ArrayList<>();
    public final ArrayList<CircleObj> circles = new ArrayList<>();
    public final ArrayList<PoisonFaucet> poisonFaucets = new ArrayList<>();
    public final ArrayList<TextObj> texts = new ArrayList<>();
    public final ArrayList<RotateTrigger> triggers = new ArrayList<>();

    public double cupW() { return CUP_W; }
    public double cupH() { return CUP_H; }
    public double cupRimY() { return cupBottomY - CUP_H; }
    public double cupLeft() { return cupCx - CUP_W * 0.5; }
    public double cupRight() { return cupCx + CUP_W * 0.5; }
    public double cupInnerLeft() { return cupCx - CUP_W * 0.5 + CUP_WALL; }
    public double cupInnerRight() { return cupCx + CUP_W * 0.5 - CUP_WALL; }
    public double cupInnerTop() { return cupRimY(); }
    public double cupInnerBottom() { return cupBottomY - CUP_WALL; }

    public static Level createDefault() {
        Level L = new Level();
        L.faucetX = 0.30;
        L.cupCx = 0.30;
        L.cupBottomY = 0.940;
        return L;
    }
}
