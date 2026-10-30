package net.blandfordsfox.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.blandfordsfox.entity.BlandfordsFox;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

public class BlandfordsFoxRenderer extends GeoEntityRenderer<BlandfordsFox> {
    public BlandfordsFoxRenderer(EntityRendererProvider.Context context) {
        super(context, new BlandfordsFoxModel());
        this.shadowRadius = 0.35F;
        addRenderLayer(new MouthItemLayer(this));
    }

    /** Cubs are drawn at half size, like vanilla. */
    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, BlandfordsFox fox,
                                    BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        if (!isReRender && fox.isBaby()) {
            widthScale *= 0.5F;
            heightScale *= 0.5F;
        }
        super.scaleModelForRender(widthScale, heightScale, poseStack, fox, model, isReRender, partialTick, packedLight, packedOverlay);
    }

    /**
     * Like vanilla's FoxRenderer, tilt the fox along its arc while pouncing, but only
     * while it's in the air (or stuck nose-first in snow), and about the middle of its
     * body rather than its feet. Vanilla tilts about the feet, which on landing left
     * the front half of this model pitched down into the ground.
     */
    @Override
    protected void applyRotations(BlandfordsFox fox, PoseStack poseStack, float ageInTicks, float rotationYaw,
                                  float partialTick, float nativeScale) {
        super.applyRotations(fox, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
        boolean airborne = fox.isPouncing() && !fox.onGround();
        if (airborne || fox.isFaceplanted()) {
            float pitch = Mth.clamp(-Mth.lerp(partialTick, fox.xRotO, fox.getXRot()), -45F, 45F);
            float mid = fox.getBbHeight() * 0.5F / nativeScale;
            poseStack.translate(0, mid, 0);
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
            poseStack.translate(0, -mid, 0);
        }
    }

    /** Draws whatever the fox has picked up (berries, eggs, swords...) in its mouth. */
    private static class MouthItemLayer extends BlockAndItemGeoLayer<BlandfordsFox> {
        MouthItemLayer(BlandfordsFoxRenderer renderer) {
            super(renderer);
        }

        @Override
        protected ItemStack getStackForBone(GeoBone bone, BlandfordsFox fox) {
            if (!"mouth_item".equals(bone.getName())) return null;
            ItemStack stack = fox.getItemBySlot(EquipmentSlot.MAINHAND);
            return stack.isEmpty() ? null : stack;
        }

        @Override
        protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, BlandfordsFox fox) {
            return ItemDisplayContext.GROUND;
        }

        @Override
        protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, BlandfordsFox fox,
                                          MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.XP.rotationDegrees(90));   // lie the item flat across the jaws, as vanilla does
            poseStack.translate(0, -0.12, 0);
            poseStack.scale(0.75F, 0.75F, 0.75F);
            super.renderStackForBone(poseStack, bone, stack, fox, bufferSource, partialTick, packedLight, packedOverlay);
            poseStack.popPose();
        }
    }
}
