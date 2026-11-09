package net.maidens.village;

import net.maidens.maiden.Maiden;
import net.maidens.network.VillageActionPayload;
import net.maidens.network.VillageViewPayload;
import net.maidens.politics.Diplomacy;
import net.maidens.politics.KnownVillage;
import net.maidens.politics.Network;
import net.maidens.politics.Stance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;

/**
 * The village screen (opened at the bell): everything it shows, gathered on the server, and what's done on it.
 * The map is a top-down picture of the village in map colours, at most 256 x 256 (one pixel per block, or per two).
 */
public final class VillageView {
    private static final int MARGIN = 24, MAX_MAP = 256;

    private VillageView() {}

    public static void open(ServerLevel level, VillageRecord v, ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, new VillageViewPayload(VillageViewPayload.VILLAGE, build(level, v, p)));
    }

    public static CompoundTag build(ServerLevel level, VillageRecord v, ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        Network network = Network.get(level);
        KnownVillage self = network.know(level, v);
        List<Maiden> residents = VillageSocial.residents(level, v);
        t.putLong("Id", v.id);
        t.putString("Name", v.name);
        t.putString("Style", v.style);
        String royal = "";
        for (Maiden m : residents) if (v.isRoyal(m.getUUID())) royal = m.name();
        t.putString("Royal", royal);
        t.putBoolean("Citizen", v.isCitizen(p.getUUID()));
        String right = Diplomacy.rightToDecide(level, v, p);
        t.putString("Right", right == null ? "" : right);
        t.putString("HowTo", Diplomacy.howToEarnIt(level, v, p));
        t.putBoolean("Building", v.blueprint.isBuilding());
        t.putString("Project", v.project);
        map(level, v, t);
        ListTag pieces = new ListTag();
        for (Blueprint.PieceInfo pi : v.blueprint.pieces()) {
            if (pi.name().equals("road") || pi.name().equals(Buildings.NOT_A_BUILDING) || pi.name().equals(Walls.NAME)) continue;
            CompoundTag pt = new CompoundTag();
            pt.putIntArray("Box", new int[]{pi.box().minX(), pi.box().minZ(), pi.box().maxX(), pi.box().maxZ()});
            pt.putString("Name", pi.name().isEmpty() ? "" : Buildings.displayName(ResourceLocation.withDefaultNamespace(pi.name()), v.style));
            pt.putBoolean("New", pi.building());
            pieces.add(pt);
        }
        t.put("Pieces", pieces);
        ListTag people = new ListTag();
        for (Maiden m : residents) {
            CompoundTag mt = new CompoundTag();
            mt.putString("Name", m.name());
            mt.putString("Title", m.title());
            BlockPos bed = m.residence().bed();
            if (bed != null) mt.putIntArray("Bed", new int[]{bed.getX(), bed.getZ()});
            mt.putBoolean("Royal", m.isRoyal());
            people.add(mt);
        }
        t.put("Residents", people);
        ListTag news = new ListTag();
        for (String line : network.news(v.id)) news.add(StringTag.valueOf(line));
        t.put("News", news);
        ListTag templates = new ListTag();
        for (ResourceLocation tpl : Buildings.templates(level, v.style)) {
            CompoundTag tt = new CompoundTag();
            tt.putString("Name", Buildings.shortName(tpl));
            tt.putString("Display", Buildings.displayName(tpl, v.style));
            tt.putInt("Beds", Buildings.beds(level, tpl));
            var size = level.getStructureManager().getOrCreate(tpl).getSize();
            tt.putIntArray("Size", new int[]{size.getX(), size.getY(), size.getZ()});
            templates.add(tt);
        }
        // the castle and walls, while the village doesn't have them
        if (v.castle == null) templates.add(0, card(Castle.NAME, "castle", 2, new int[]{Castle.SIZE, Castle.HEIGHT, Castle.SIZE}));
        if (v.wallBox == null) templates.add(v.castle == null ? 1 : 0, card(Walls.NAME, "walls", 0, new int[]{1, 1, 1}));
        t.put("Templates", templates);
        ListTag nb = new ListTag();
        for (KnownVillage o : network.neighbours(self)) {
            CompoundTag nt = new CompoundTag();
            nt.putLong("Id", o.id);
            nt.putString("Name", o.name);
            nt.putInt("Distance", (int) Math.round(self.distanceTo(o)));
            nt.putInt("Ours", network.stance(self, o).ordinal());
            nt.putInt("Theirs", network.stance(o, self).ordinal());
            nb.add(nt);
        }
        t.put("Neighbours", nb);
        return t;
    }

    /** A top-down picture of the village and its surroundings, in vanilla map colours (packed ids, 0 where not loaded). */
    private static void map(ServerLevel level, VillageRecord v, CompoundTag t) {
        // the village, its castle and its walls
        int bx0 = v.box.minX(), bz0 = v.box.minZ(), bx1 = v.box.maxX(), bz1 = v.box.maxZ();
        for (var b : new net.minecraft.world.level.levelgen.structure.BoundingBox[]{v.castle, v.wallBox}) {
            if (b == null) continue;
            bx0 = Math.min(bx0, b.minX());
            bz0 = Math.min(bz0, b.minZ());
            bx1 = Math.max(bx1, b.maxX());
            bz1 = Math.max(bz1, b.maxZ());
        }
        int x0 = bx0 - MARGIN, z0 = bz0 - MARGIN;
        int w = bx1 - bx0 + 1 + 2 * MARGIN, h = bz1 - bz0 + 1 + 2 * MARGIN;
        int step = Math.max(1, (Math.max(w, h) + MAX_MAP - 1) / MAX_MAP);
        int pw = w / step, ph = h / step;
        byte[] colours = new byte[pw * ph];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int px = 0; px < pw; px++) {
            for (int pz = 0; pz < ph; pz++) {
                int x = x0 + px * step, z = z0 + pz * step;
                if (!level.hasChunkAt(pos.set(x, 0, z))) continue;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                BlockState s = level.getBlockState(pos.set(x, y, z));
                MapColor c = s.getMapColor(level, pos);
                if (c == MapColor.NONE) continue;
                colours[pz * pw + px] = c.getPackedId(MapColor.Brightness.NORMAL);     // flat colours: a simple map
            }
        }
        t.putIntArray("Map", new int[]{x0, z0, pw, ph, step});
        t.putByteArray("Colours", colours);
    }

    /** Something done on a village screen. */
    public static void handle(ServerPlayer p, VillageActionPayload a) {
        ServerLevel level = p.serverLevel();
        if (a.action() == VillageActionPayload.TEMPLATE) {
            sendTemplate(level, p, a.village(), a.text());
            return;
        }
        if (a.action() == VillageActionPayload.WORLD_MAP) {
            net.maidens.politics.WorldMap.send(level, p, a.x(), a.z(), a.number() == 1, a.village());
            return;
        }
        VillageRecord v = Villages.get(level).byId(a.village());
        if (v == null || p.distanceToSqr(v.centre().getX(), p.getY(), v.centre().getZ()) > 256 * 256) return;
        String reply = switch (a.action()) {
            case VillageActionPayload.BUILD -> VillageMenu.buildAt(level, v, p, a.text(), new BlockPos(a.x(), 0, a.z()), Rotation.values()[Math.floorMod(a.extra(), 4)]);
            case VillageActionPayload.STANCE -> {
                KnownVillage other = Network.get(level).village(a.number());
                yield other == null ? "There's no such village." : Diplomacy.setStance(level, v, p, other, Stance.byId(a.extra()));
            }
            default -> null;
        };
        if (reply != null) p.sendSystemMessage(Component.literal(reply));
        open(level, v, p);
    }

    private static CompoundTag card(String name, String display, int beds, int[] size) {
        CompoundTag tt = new CompoundTag();
        tt.putString("Name", name);
        tt.putString("Display", display);
        tt.putInt("Beds", beds);
        tt.putIntArray("Size", size);
        return tt;
    }

    /** A building's blocks (unrotated), for the picture on its card: block state ids and packed positions. */
    private static void sendTemplate(ServerLevel level, ServerPlayer p, long village, String name) {
        VillageRecord v = Villages.get(level).byId(village);
        if (v == null) return;
        Map<BlockPos, BlockState> blocks;
        int[] size;
        if (name.equals(Castle.NAME)) {
            blocks = Castle.design();
            size = new int[]{Castle.SIZE, Castle.HEIGHT, Castle.SIZE};
        } else if (name.equals(Walls.NAME)) {
            blocks = Walls.preview();
            size = new int[]{15, 10, 8};
        } else {
            ResourceLocation tpl = VillageMenu.templateNamed(level, v, name);
            if (tpl == null) return;
            blocks = Buildings.preview(level, tpl).blocks();
            var ts = level.getStructureManager().getOrCreate(tpl).getSize();
            size = new int[]{ts.getX(), ts.getY(), ts.getZ()};
        }
        int n = blocks.size(), i = 0;
        int[] states = new int[n], positions = new int[n];
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            BlockPos q = e.getKey();
            states[i] = Block.getId(e.getValue());
            positions[i] = (q.getX() & 0x3FF) | (q.getY() & 0x3FF) << 10 | (q.getZ() & 0x3FF) << 20;
            i++;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putIntArray("States", states);
        tag.putIntArray("Positions", positions);
        tag.putIntArray("Size", size);
        PacketDistributor.sendToPlayer(p, new VillageViewPayload(VillageViewPayload.TEMPLATE, tag));
    }
}
