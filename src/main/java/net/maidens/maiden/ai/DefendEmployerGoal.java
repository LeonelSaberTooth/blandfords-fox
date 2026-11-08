package net.maidens.maiden.ai;

import net.maidens.maiden.Maiden;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** A hired guard goes for whatever attacks her employer, or whatever her employer attacks (players and maidens aside). */
final class DefendEmployerGoal extends TargetGoal {
    private final Maiden m;
    private LivingEntity enemy;
    private int lastHurtBy, lastHurt;

    DefendEmployerGoal(Maiden m) {
        super(m, false);
        this.m = m;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!m.employment().isGuarding()) return false;
        Player p = m.employment().employerPlayer();
        if (p == null) return false;
        LivingEntity a = p.getLastHurtByMob();
        if (a != null && p.getLastHurtByMobTimestamp() != lastHurtBy && ok(a)) {
            enemy = a;
            lastHurtBy = p.getLastHurtByMobTimestamp();
            return true;
        }
        LivingEntity b = p.getLastHurtMob();
        if (b != null && p.getLastHurtMobTimestamp() != lastHurt && ok(b)) {
            enemy = b;
            lastHurt = p.getLastHurtMobTimestamp();
            return true;
        }
        return false;
    }

    private boolean ok(LivingEntity e) {
        return e != m && !(e instanceof Player) && !(e instanceof Maiden) && e.isAlive() && canAttack(e, TargetingConditions.DEFAULT);
    }

    @Override
    public void start() {
        mob.setTarget(enemy);
        super.start();
    }
}
