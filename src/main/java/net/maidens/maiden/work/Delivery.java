package net.maidens.maiden.work;

import net.maidens.block.MarkedChestBlockEntity;
import net.maidens.maiden.Maiden;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;

/**
 * Taking what she gathered to a marked chest: the closest one within 32 blocks of where she was hired.
 * She keeps her own things (tools, weapons, armor, torches, saplings) and a stack of seeds and of filler blocks
 * for her work. Walking there is done by {@link DeliverGoal}.
 */
public final class Delivery {
    public static final int RANGE = 32;

    private Delivery() {}

    /** Sends her to the marked chest if there is one; false if there isn't. */
    static boolean start(Employment e, boolean resume) {
        Maiden m = e.maiden();
        if (e.workPos == null || !(m.level() instanceof ServerLevel level)) return false;
        MarkedChestBlockEntity chest = MarkedChestBlockEntity.nearest(level, e.workPos, RANGE);
        if (chest == null) return false;
        e.deliverTo = chest.getBlockPos();
        e.resumeAfterDelivery = resume;
        return true;
    }

    /** What stays in her bag: what she works with, not what she gathered. */
    public static boolean keeps(ItemStack s) {
        return s.isDamageableItem() || s.is(Items.TORCH) || s.is(ItemTags.SAPLINGS);
    }

    /** She's at the chest: puts in what fits. Returns false if the chest was full (some is left in her bag). */
    static boolean unload(Employment e, MarkedChestBlockEntity chest) {
        Maiden m = e.maiden();
        SimpleContainer bag = e.bag();
        int seeds = 0, filler = 0;
        boolean allFit = true;
        for (int i = 0; i < bag.getContainerSize(); i++) {
            ItemStack s = bag.getItem(i);
            if (s.isEmpty() || keeps(s)) continue;
            // a stack of seeds for a farmer, and of filler blocks for a miner, stay with her
            if (isSeed(s) && seeds < 64) { seeds += s.getCount(); continue; }
            if (isFiller(s) && filler < 64) { filler += s.getCount(); continue; }
            ItemStack rest = put(chest, s.copy());
            bag.setItem(i, rest);
            if (!rest.isEmpty()) allFit = false;
        }
        bag.setChanged();
        chest.setChanged();
        m.swing(InteractionHand.MAIN_HAND);
        m.level().playSound(null, chest.getBlockPos(), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
        if (!allFit && e.employerPlayer() instanceof ServerPlayer sp) m.say(sp, "The marked chest is full! Some of it's still in my bag.");
        return allFit;
    }

    static boolean isSeed(ItemStack s) {
        return s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CropBlock;
    }

    static boolean isFiller(ItemStack s) {
        return s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE) || s.is(Items.DIRT);
    }

    /** Merges into the chest's matching stacks, then its empty slots. Returns what didn't fit. */
    private static ItemStack put(MarkedChestBlockEntity chest, ItemStack stack) {
        for (int i = 0; i < chest.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack in = chest.getItem(i);
            if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, stack) && in.getCount() < in.getMaxStackSize()) {
                int move = Math.min(stack.getCount(), in.getMaxStackSize() - in.getCount());
                in.grow(move);
                stack.shrink(move);
            }
        }
        for (int i = 0; i < chest.getContainerSize() && !stack.isEmpty(); i++) {
            if (chest.getItem(i).isEmpty()) {
                chest.setItem(i, stack.copy());
                stack.setCount(0);
            }
        }
        return stack;
    }
}
