package net.maidens.maiden.ai;

import net.maidens.maiden.Maiden;
import net.maidens.maiden.war.RaidGoals;
import net.maidens.maiden.work.DeliverGoal;
import net.maidens.maiden.work.WorkGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.player.Player;

/**
 * Every goal a maiden has, in priority order (lower number first). This is the one place to see what
 * overrides what: talking and trading stop everything; then danger; then a guard's duties; then hired work or
 * following a player (or, for a village resident, repairs and her village job); then her day.
 */
public final class MaidenAi {
    private MaidenAi() {}

    public static void register(Maiden m, GoalSelector goals, GoalSelector targets) {
        goals.addGoal(0, new FloatGoal(m));
        goals.addGoal(1, new TradeWithPlayerGoal(m));
        goals.addGoal(1, new LookAtTradingPlayerGoal(m));
        goals.addGoal(1, new TalkGoal(m));
        goals.addGoal(1, new RaidGoals.Flee(m));            // a beaten raider running home
        goals.addGoal(2, new FleeMonstersGoal(m));
        goals.addGoal(2, new GuardAttackGoal(m));
        goals.addGoal(3, new FollowEmployerGoal(m));
        goals.addGoal(3, new FollowPlayerGoal(m));          // walking with a player who asked her to
        goals.addGoal(3, new RaidGoals.FollowLeader(m));    // on the way to a raid a player leads
        goals.addGoal(3, new DeliverGoal(m));               // taking her bag to a marked chest
        goals.addGoal(3, new VillageChoreGoal(m));          // repairs and building (a few workers leave their work for it)
        goals.addGoal(4, new WorkGoal(m));
        goals.addGoal(4, new RaidGoals.Advance(m));         // a raider pushing into the village
        goals.addGoal(4, new net.maidens.maiden.work.VillageWorkGoal(m));   // her village job, at her spot
        goals.addGoal(5, new ScheduleGoal(m));              // a resident's day
        goals.addGoal(5, new MoveTowardsRestrictionGoal(m, 0.6));
        goals.addGoal(6, new OpenDoorGoal(m, true));
        goals.addGoal(7, new WanderGoal(m));
        goals.addGoal(8, new LookAtPlayerGoal(m, Player.class, 8.0F));
        goals.addGoal(9, new RandomLookAroundGoal(m));
        targets.addGoal(1, new RetaliateGoal(m));
        targets.addGoal(2, new DefendEmployerGoal(m));
        targets.addGoal(2, new RaidGoals.Targets(m));        // a raider's enemies
        targets.addGoal(3, new RaidGoals.Defend(m));         // raiders, for her village's fighters
        targets.addGoal(3, new HuntMonstersGoal(m));
    }
}
