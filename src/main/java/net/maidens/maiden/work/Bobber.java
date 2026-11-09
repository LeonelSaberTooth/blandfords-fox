package net.maidens.maiden.work;

import net.maidens.core.ModEntities;
import net.maidens.maiden.Maiden;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The bobber on a fishing maiden's line, for the look of it: {@link FishTask} moves it (the cast, bobbing,
 * the dip when a fish bites) and removes it when she reels in. The client draws it with her line
 * (BobberRenderer). Removes itself if she's gone.
 */
public class Bobber extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(Bobber.class, EntityDataSerializers.INT);
    private int age;

    public Bobber(EntityType<? extends Bobber> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    static Bobber cast(Maiden m, Vec3 at) {
        Bobber b = new Bobber(ModEntities.BOBBER.get(), m.level());
        b.entityData.set(OWNER, m.getId());
        b.moveTo(at.x, at.y, at.z, 0, 0);
        m.level().addFreshEntity(b);
        return b;
    }

    @Nullable
    public Maiden owner() {
        return level().getEntity(entityData.get(OWNER)) instanceof Maiden m ? m : null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, -1);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        Maiden o = owner();
        if (o == null || !o.isAlive() || o.distanceToSqr(this) > 32 * 32 || ++age > 6000) discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
