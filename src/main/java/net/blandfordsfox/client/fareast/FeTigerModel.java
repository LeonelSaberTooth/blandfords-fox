package net.blandfordsfox.client.fareast;

import com.chickendesigner.fareast.client.TigerAnims;
import com.chickendesigner.fareast.entity.Tiger;
import com.chickendesigner.fareast.entity.TigerAnimationState;
import net.blandfordsfox.compat.FeTiger;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Far East compatibility: Far East's adult tiger on the model the new animations were made for,
 * posed the way Far East poses it (same weights and blends) with the new walk, gallop, stretch
 * and roar in place of Far East's, plus grooming while resting and the three tree animations.
 * Far East's own idle, sneak, rest, sleep, lick, pounce and swipe still play as they are.
 * Walk and gallop are driven by the distance walked, so the paws keep pace with the ground.
 */
public class FeTigerModel extends HierarchicalModel<Tiger> {
    private static final Vector3f CACHE = new Vector3f();
    private static final float SNEAK_BLOCKS_PER_CYCLE = 1.5F, REST_DROP = 10.96F, SLEEP_DROP = 14.62F;

    private final ModelPart root, whole, head;

    public FeTigerModel(ModelPart root) {
        this.root = root;
        this.whole = root.getChild("tiger");
        this.head = this.whole.getChild("hips").getChild("body").getChild("chest").getChild("head");
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    /** Which of ours an action plays, by name in FeTigerData. */
    static String ourAnim(int action) {
        return switch (action) {
            case FeTiger.STRETCH -> "STRETCH";
            case FeTiger.ROAR -> "CALL";
            case FeTiger.MARK -> "MARK";
            case FeTiger.CHEEK_RUB -> "CHEEK_RUB";
            case FeTiger.SPRAY -> "SPRAY";
            case FeTiger.GROOM -> "REST_GROOM";
            default -> null;
        };
    }

    /** Seconds into an action's animation (STRETCH plays at 0.74x). */
    static float animSeconds(int action, float actionSeconds) {
        return action == FeTiger.STRETCH ? actionSeconds * FeTiger.STRETCH_SPEED : actionSeconds;
    }

    @Override
    public void setupAnim(Tiger tiger, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        TigerAnimationState state = tiger.animation;
        float partial = Mth.frac(ageInTicks);
        float seconds = ageInTicks / 20.0F;
        float rest = state.rest(partial);
        float sleep = state.sleep(partial);
        float stand = 1.0F - Mth.clamp(rest + sleep, 0.0F, 1.0F);
        int action = state.shownAction();
        float actionWeight = state.actionWeight(ageInTicks);
        float actionSeconds = state.actionSeconds(ageInTicks);
        boolean fullBody = action == 1 || action == FeTiger.STRETCH || action == 4
                || action == FeTiger.MARK || action == FeTiger.CHEEK_RUB || action == FeTiger.SPRAY;
        float base = stand * (fullBody ? 1.0F - actionWeight : 1.0F);
        // the head follows what it looks at, except while an animation moves it (the roar moves only the head)
        float look = stand * (fullBody || action == FeTiger.ROAR ? 1.0F - actionWeight : 1.0F);
        this.head.yRot += Mth.wrapDegrees(netHeadYaw) * Mth.DEG_TO_RAD * look;
        this.head.xRot += headPitch * Mth.DEG_TO_RAD * look;
        float stride = state.stride(partial);
        float moving = state.moving(partial);
        float running = state.running(partial);
        float sneak = state.sneak(partial);
        float walking = moving * (1.0F - running);
        loop(idleFor(tiger, seconds), seconds, base * (1.0F - moving) * (1.0F - sneak));
        loop(TigerAnims.SNEAK_IDLE, seconds, base * (1.0F - moving) * sneak);
        cycle(FeTigerData.anim("WALK_NEW"), stride, FeTiger.WALK_BLOCKS_PER_CYCLE, base * walking * (1.0F - sneak));
        cycle(TigerAnims.SNEAK_WALK, stride, SNEAK_BLOCKS_PER_CYCLE, base * walking * sneak);
        cycle(FeTigerData.anim("GALLOP"), stride, FeTiger.RUN_BLOCKS_PER_CYCLE, base * moving * running);
        // grooming starts and ends in the resting pose, so it takes the resting pose's place
        float groom = action == FeTiger.GROOM ? actionWeight : 0.0F;
        loop(TigerAnims.REST, seconds, rest * (1.0F - groom));
        if (groom > 0.0F) loop(FeTigerData.anim("REST_GROOM"), actionSeconds, rest * groom);
        loop(TigerAnims.SLEEP, seconds, sleep);
        this.whole.y += REST_DROP * rest + SLEEP_DROP * sleep;
        if (actionWeight > 0.0F && action != FeTiger.GROOM) {
            String ours = ourAnim(action);
            if (ours != null) loop(FeTigerData.anim(ours), animSeconds(action, actionSeconds), actionWeight * stand);
            else if (action == 1) loop(TigerAnims.LICK, actionSeconds, actionWeight * stand);
            else if (action == 4) loop(TigerAnims.POUNCE, 0.0F, actionWeight * stand);
        }
        float swipe = state.swipeSeconds(ageInTicks);
        if (swipe >= 0.0F && swipe < TigerAnims.SWIPE.lengthInSeconds()) loop(TigerAnims.SWIPE, swipe, stand);
    }

    /** Far East's idle: now and then the one with the head shake (same choice as Far East makes). */
    private static AnimationDefinition idleFor(Tiger tiger, float seconds) {
        AnimationDefinition shaking = TigerAnims.IDLE;
        int loop = Mth.floor(seconds / shaking.lengthInSeconds());
        int hash = tiger.getId() * 73856093 ^ loop * 19349663;
        hash ^= hash >>> 13;
        hash *= 1540483477;
        return Math.floorMod(hash ^= hash >>> 15, 5) == 0 ? shaking : TigerAnims.IDLE_CALM;
    }

    private void cycle(AnimationDefinition animation, float stride, float blocksPerCycle, float weight) {
        loop(animation, stride / blocksPerCycle * animation.lengthInSeconds(), weight);
    }

    private void loop(AnimationDefinition animation, float seconds, float weight) {
        if (weight > 0.001F && animation != null)
            KeyframeAnimations.animate(this, animation, (long) (seconds * 1000.0F), weight, CACHE);
    }
}
