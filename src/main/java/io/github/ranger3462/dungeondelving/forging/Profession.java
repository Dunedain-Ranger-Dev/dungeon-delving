package io.github.ranger3462.dungeondelving.forging;

import java.util.ArrayList;
import java.util.List;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.skill.CraftSkill;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The crafting professions that work at a guild workshop (a {@link io.github.ranger3462.dungeondelving.guild.GuildLedgerBlock}),
 * other than Forging, which has its own {@link ForgeWorkshop}. Each needs a set of stations near the ledger; two
 * upgrades raise its workshop level, which caps the best ★ there: ★3, ★5, ★7 (see {@link CraftWorkshop}).
 */
public enum Profession {
    LEATHERWORKING(CraftSkill.LEATHERWORKING, List.of("tanning_rack", "vat", "shaping_rack"), "tanning_barrel", "fleshing_beam"),
    WEAVING(CraftSkill.WEAVING, List.of("spinning_wheel", "loom", "shaping_rack", "cauldron"), "great_loom", "dye_works"),
    WOODWORKING(CraftSkill.WOODWORKING, List.of("smoker", "chopping_block", "carving", "lathe", "cauldron"), "treadle_lathe", "bowyers_bench");

    public final CraftSkill skill;
    /** Stations the workshop needs (each id met by one or more blocks, see {@link #stations}). */
    public final List<String> required;
    public final String upgrade2;
    public final String upgrade3;
    /** Blocks that dress the workshop, widening its sweet spots a little. */
    public final TagKey<Block> decor;

    Profession(CraftSkill skill, List<String> required, String upgrade2, String upgrade3) {
        this.skill = skill;
        this.required = required;
        this.upgrade2 = upgrade2;
        this.upgrade3 = upgrade3;
        this.decor = TagKey.create(Registries.BLOCK, DungeonDelving.id(skill.id + "_decor"));
    }

    public String id() {
        return skill.id;
    }

    public Component displayName() {
        return Component.translatable("guild.dungeondelving.profession." + id());
    }

    /** The station ids this block counts as, for this profession. An upgrade can also count as the basic station it improves. */
    public List<String> stations(BlockState state) {
        List<String> ids = new ArrayList<>();
        boolean cauldron = state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON) || state.is(Crafts.SOAKING_CAULDRON.get());
        switch (this) {
            case LEATHERWORKING -> {
                if (state.is(Forging.TANNING_RACK.get())) {
                    ids.add("tanning_rack");
                }
                if (state.is(Crafts.FLESHING_BEAM.get())) {
                    ids.add("fleshing_beam");
                }
                if (cauldron || state.is(Crafts.TANNING_VAT.get()) || state.is(Crafts.TANNING_BARREL.get())) {
                    ids.add("vat");
                }
                if (state.is(Crafts.TANNING_BARREL.get())) {
                    ids.add("tanning_barrel");
                }
                if (state.is(Crafts.SHAPING_RACK.get())) {
                    ids.add("shaping_rack");
                }
            }
            case WEAVING -> {
                if (state.is(Crafts.SPINNING_WHEEL.get())) {
                    ids.add("spinning_wheel");
                }
                if (state.is(Blocks.LOOM) || state.is(Crafts.GREAT_LOOM.get())) {
                    ids.add("loom");
                }
                if (state.is(Crafts.GREAT_LOOM.get())) {
                    ids.add("great_loom");
                }
                if (state.is(Crafts.SHAPING_RACK.get())) {
                    ids.add("shaping_rack");
                }
                if (cauldron) {
                    ids.add("cauldron");
                }
                if (state.is(Crafts.DYE_WORKS.get())) {
                    ids.add("dye_works");
                }
            }
            case WOODWORKING -> {
                if (state.is(Blocks.SMOKER)) {
                    ids.add("smoker");
                }
                if (state.is(Crafts.CHOPPING_BLOCK.get())) {
                    ids.add("chopping_block");
                }
                if (state.is(Blocks.FLETCHING_TABLE) || state.is(Crafts.BOWYERS_BENCH.get())) {
                    ids.add("carving");
                }
                if (state.is(Crafts.BOWYERS_BENCH.get())) {
                    ids.add("bowyers_bench");
                }
                if (state.is(Crafts.WOOD_LATHE.get()) || state.is(Crafts.TREADLE_LATHE.get())) {
                    ids.add("lathe");
                }
                if (state.is(Crafts.TREADLE_LATHE.get())) {
                    ids.add("treadle_lathe");
                }
                if (cauldron) {
                    ids.add("cauldron");
                }
            }
        }
        return ids;
    }
}
