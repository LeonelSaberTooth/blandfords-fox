package com.chickendesigner.fareast.client;

import com.chickendesigner.fareast.entity.Tiger;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Stand-in for compiling only (the real class comes with Far East). */
public class TigerRenderer extends MobRenderer<Tiger, HierarchicalModel<Tiger>> {
    public TigerRenderer(EntityRendererProvider.Context context) {
        super(context, null, 0.9F);
    }

    @Override
    public ResourceLocation getTextureLocation(Tiger tiger) { throw new AssertionError(); }
}
