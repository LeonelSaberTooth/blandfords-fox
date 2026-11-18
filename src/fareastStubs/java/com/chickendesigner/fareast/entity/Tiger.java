package com.chickendesigner.fareast.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;

/** Stand-in for compiling only (the real class comes with Far East). */
public abstract class Tiger extends Animal {
    public final TigerAnimationState animation = null;

    protected Tiger(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    public int getActivity() { throw new AssertionError(); }

    public int getMood() { throw new AssertionError(); }

    public int getAction() { throw new AssertionError(); }

    public boolean isStalking() { throw new AssertionError(); }

    public boolean isLyingDown() { throw new AssertionError(); }
}
