package net.maidens.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.maidens.maiden.Maiden;
import net.maidens.maiden.work.Bobber;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A maiden's fishing bobber and line, drawn like a player's (the vanilla bobber texture, a black sagging line). */
public class BobberRenderer extends EntityRenderer<Bobber> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
    private static final RenderType BOBBER = RenderType.entityCutout(TEXTURE);

    public BobberRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(Bobber bobber, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        Maiden owner = bobber.owner();
        if (owner == null) return;
        poseStack.pushPose();
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer quad = buffers.getBuffer(BOBBER);
        corner(quad, pose, light, 0.0F, 0, 0, 1);
        corner(quad, pose, light, 1.0F, 0, 1, 1);
        corner(quad, pose, light, 1.0F, 1, 1, 0);
        corner(quad, pose, light, 0.0F, 1, 0, 0);
        poseStack.popPose();
        // the line, from the tip of the rod in her right hand to the bobber
        Vec3 tip = rodTip(owner, partialTick);
        Vec3 at = bobber.getPosition(partialTick).add(0, 0.25, 0);
        float dx = (float) (tip.x - at.x), dy = (float) (tip.y - at.y), dz = (float) (tip.z - at.z);
        VertexConsumer line = buffers.getBuffer(RenderType.lineStrip());
        PoseStack.Pose linePose = poseStack.last();
        for (int i = 0; i <= 16; i++) lineVertex(dx, dy, dz, line, linePose, i / 16.0F, (i + 1) / 16.0F);
        poseStack.popPose();
        super.render(bobber, yaw, partialTick, poseStack, buffers, light);
    }

    /** Roughly where the rod's tip is: in front of her right shoulder, a little above it. */
    private static Vec3 rodTip(Maiden m, float partialTick) {
        float yaw = Mth.lerp(partialTick, m.yBodyRotO, m.yBodyRot) * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yaw), cos = Mth.cos(yaw);
        double right = 0.35, forward = 0.9;
        Vec3 base = m.getPosition(partialTick);
        return new Vec3(base.x - cos * right - sin * forward, base.y + m.getEyeHeight() - 0.15, base.z - sin * right + cos * forward);
    }

    private static void corner(VertexConsumer c, PoseStack.Pose pose, int light, float x, int y, int u, int v) {
        c.addVertex(pose, x - 0.5F, y - 0.5F, 0.0F).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void lineVertex(float x, float y, float z, VertexConsumer c, PoseStack.Pose pose, float t, float t2) {
        float px = x * t, py = y * (t * t + t) * 0.5F + 0.25F, pz = z * t;
        float nx = x * t2 - px, ny = y * (t2 * t2 + t2) * 0.5F + 0.25F - py, nz = z * t2 - pz;
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0) { nx /= len; ny /= len; nz /= len; }
        c.addVertex(pose, px, py, pz).setColor(0xFF000000).setNormal(pose, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(Bobber bobber) {
        return TEXTURE;
    }
}
