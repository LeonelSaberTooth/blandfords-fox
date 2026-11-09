package net.maidens.client.village;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Draws a building's blocks as a small isometric picture inside a box on screen. */
public final class IsoRenderer {
    private IsoRenderer() {}

    public static void render(GuiGraphics g, TemplateCache.Building b, int x, int y, int w, int h, float turn) {
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        // fit the building's diagonal and height into the box
        float across = (float) Math.sqrt(b.sx() * b.sx() + b.sz() * b.sz());
        float scale = Math.min(w / (across + 1.0F), h / (across * 0.5F + b.sy() * 0.82F + 1.0F));
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x + w / 2.0F, y + h / 2.0F, 150.0F);
        ps.scale(scale, -scale, scale);
        ps.mulPose(Axis.XP.rotationDegrees(30.0F));
        ps.mulPose(Axis.YP.rotationDegrees(225.0F + turn));
        ps.translate(-b.sx() / 2.0F, -b.sy() / 2.0F, -b.sz() / 2.0F);
        Lighting.setupFor3DItems();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        for (TemplateCache.Placed p : b.visible()) {
            ps.pushPose();
            ps.translate(p.x(), p.y(), p.z());
            blocks.renderSingleBlock(p.state(), ps, buffers, 0xF000F0, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        g.flush();
        Lighting.setupForFlatItems();
        ps.popPose();
    }
}
