package net.blandfordsfox.client.fareast;

import com.chickendesigner.fareast.client.TigerRenderer;
import com.chickendesigner.fareast.entity.Tiger;
import com.mojang.blaze3d.vertex.PoseStack;
import net.blandfordsfox.BlandfordsFoxMod;
import net.blandfordsfox.compat.FeTiger;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Far East compatibility: draws adult tigers with the new model and animations, and swaps the
 * face textures the way the animations were made to (eyes shut while stretching, grooming and
 * rubbing its cheek, the open-mouth face for the yawn and the roar), and the running texture
 * (front-leg tops hidden) while it gallops. Cubs are drawn by Far East as before.
 */
public class FeTigerRenderer extends MobRenderer<Tiger, FeTigerModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "fe_tiger"), "main");

    private final TigerRenderer farEast;

    public FeTigerRenderer(EntityRendererProvider.Context context) {
        super(context, new FeTigerModel(context.bakeLayer(LAYER)), 0.9F);
        this.farEast = new TigerRenderer(context);
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(BlandfordsFoxMod.MOD_ID, "textures/entity/fe_tiger/" + name + ".png");
    }

    private static final ResourceLocation MAIN = tex("main"), SLEEP = tex("sleep"), STRETCH = tex("stretch"), ROAR = tex("roar"),
            GROWL = tex("growl"), ANGRY = tex("angry"), RUN = tex("run"), RUN_GROWL = tex("run_growl"), RUN_ANGRY = tex("run_angry");

    @Override
    public void render(Tiger tiger, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (tiger.isBaby()) {
            this.farEast.render(tiger, yaw, partialTicks, poseStack, buffers, light);
            return;
        }
        super.render(tiger, yaw, partialTicks, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Tiger tiger) {
        if (tiger.isBaby()) return this.farEast.getTextureLocation(tiger);
        if (tiger.getActivity() == 2) return SLEEP;
        int action = tiger.animation.shownAction();
        String anim = FeTigerModel.ourAnim(action);
        if (anim != null && tiger.animation.actionWeight(tiger.tickCount) > 0) {
            String face = FeTigerData.face(anim, FeTigerModel.animSeconds(action, tiger.animation.actionSeconds(tiger.tickCount)));
            if (face != null) return switch (face) {
                case "sleep" -> SLEEP;
                case "stretch" -> STRETCH;
                case "roar" -> ROAR;
                default -> MAIN;
            };
            if (action == FeTiger.STRETCH || action == FeTiger.ROAR) return MAIN;
        }
        boolean galloping = tiger.animation.running(1.0F) > 0.5F && tiger.animation.moving(1.0F) > 0.5F;
        if (tiger.getMood() == 1) return galloping ? RUN_GROWL : GROWL;
        if (tiger.getMood() == 2) return galloping ? RUN_ANGRY : ANGRY;
        return galloping ? RUN : MAIN;
    }
}
