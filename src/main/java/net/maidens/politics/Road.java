package net.maidens.politics;

import net.minecraft.nbt.CompoundTag;

import java.util.BitSet;

/**
 * A road between two maiden villages: a straight line from the edge of one to the edge of the other, built a
 * stretch at a time from both ends ({@link Roads}). Which of its steps are already laid is remembered, since a step
 * can only be laid while its land is loaded.
 */
public final class Road {
    /** How far from a village's centre the road starts (its own streets cover the middle). */
    static final int VILLAGE_RADIUS = 48;

    public final long a, b;
    final int ax, az, bx, bz;
    /** Steps built so far from each end. */
    int fromA, fromB;
    final BitSet laid = new BitSet();

    Road(KnownVillage va, KnownVillage vb) {
        this(va.id, vb.id, va.x, va.z, vb.x, vb.z);
    }

    private Road(long a, long b, int ax, int az, int bx, int bz) {
        this.a = a;
        this.b = b;
        this.ax = ax;
        this.az = az;
        this.bx = bx;
        this.bz = bz;
    }

    /** Steps (blocks) from edge to edge. */
    public int length() {
        double d = Math.sqrt((double) (bx - ax) * (bx - ax) + (double) (bz - az) * (bz - az));
        return Math.max(0, (int) d - 2 * VILLAGE_RADIUS);
    }

    /** Where step i is (x, z). */
    int[] step(int i) {
        double d = Math.sqrt((double) (bx - ax) * (bx - ax) + (double) (bz - az) * (bz - az));
        double t = (VILLAGE_RADIUS + i) / d;
        return new int[]{(int) Math.round(ax + (bx - ax) * t), (int) Math.round(az + (bz - az) * t)};
    }

    public boolean finished() {
        return fromA + fromB >= length();
    }

    /** How much is built, 0 to 1. */
    public double progress() {
        int len = length();
        return len == 0 ? 1 : Math.min(1.0, (fromA + fromB) / (double) len);
    }

    /** Built steps, for the world map: up to which step from a, and from which step on from b. */
    public int builtFromA() {
        return Math.min(fromA, length());
    }

    public int builtFromB() {
        return Math.min(fromB, length());
    }

    public int laidCount() {
        return laid.cardinality();
    }

    public int[] ends() {
        return new int[]{ax, az, bx, bz};
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("A", a);
        t.putLong("B", b);
        t.putIntArray("Ends", new int[]{ax, az, bx, bz});
        t.putInt("FromA", fromA);
        t.putInt("FromB", fromB);
        t.putLongArray("Laid", laid.toLongArray());
        return t;
    }

    static Road load(CompoundTag t) {
        int[] e = t.getIntArray("Ends");
        if (e.length != 4) e = new int[4];
        Road r = new Road(t.getLong("A"), t.getLong("B"), e[0], e[1], e[2], e[3]);
        r.fromA = t.getInt("FromA");
        r.fromB = t.getInt("FromB");
        r.laid.or(BitSet.valueOf(t.getLongArray("Laid")));
        return r;
    }
}
