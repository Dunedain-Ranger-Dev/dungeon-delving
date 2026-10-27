package io.github.ranger3462.dungeondelving.integration.spellengine;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.forging.ForgedGear;
import io.github.ranger3462.dungeondelving.forging.Forging;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellChoice;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.container.SpellContainers;

/**
 * Forged staves and wands as RPG Series casters (DD-92), when Spell Engine is installed. Like the Wizards mod's Wizard
 * Staff, each is a magic weapon with a choice of spells to bind (its pool, {@code data/dungeondelving/tags/spell/weapon/}):
 * the first use offers the choice, and the spell chosen is the one it casts. It gives flat spell power by its ★
 * ({@link ForgedGear#STAFF_SPELL_POWER}, {@link ForgedGear#WAND_SPELL_POWER}) to its bound spell's school, or before a
 * spell's chosen, to every school it could bind.
 */
public final class ForgedCasting {
    public static final ResourceLocation STAFF_POOL = DungeonDelving.id("weapon/forged_staff");
    public static final ResourceLocation WAND_POOL = DungeonDelving.id("weapon/forged_wand");

    /** Each spell in the pools, and its school (as its spell power attribute's path). Kept in step with the pool tags. */
    private static final Map<String, String> STAFF_SPELLS = Map.of("wizards:arcane_blast", "arcane", "wizards:fire_blast", "fire",
            "wizards:frostbolt", "frost", "elemental_wizards_rpg:aqua_water_whip", "water", "elemental_wizards_rpg:terra_stone_spear", "earth",
            "elemental_wizards_rpg:wind_air_cutter", "air");
    private static final Map<String, String> WAND_SPELLS = Map.of("wizards:arcane_bolt", "arcane", "wizards:fireball", "fire",
            "wizards:frost_shard", "frost");

    private ForgedCasting() {}

    /** Called from the mod constructor when Spell Engine is loaded. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ForgedCasting::defaults);
        ForgedGear.spellSchools = ForgedCasting::schools;
    }

    private static void defaults(ModifyDefaultComponentsEvent event) {
        event.modify(Forging.FORGED_STAFF.get(), builder -> builder
                .set(SpellDataComponents.SPELL_CONTAINER, SpellContainers.forMagicWeapon().withBindingPool(STAFF_POOL))
                .set(SpellDataComponents.SPELL_CHOICE, SpellChoice.of(STAFF_POOL.toString())));
        event.modify(Forging.FORGED_WAND.get(), builder -> builder
                .set(SpellDataComponents.SPELL_CONTAINER, SpellContainers.forMagicWeapon().withBindingPool(WAND_POOL))
                .set(SpellDataComponents.SPELL_CHOICE, SpellChoice.of(WAND_POOL.toString())));
    }

    /** The schools a staff or wand empowers: its bound spell's, or before one's chosen, every school it could bind. */
    static List<String> schools(ItemStack stack) {
        Map<String, String> pool = stack.is(Forging.FORGED_WAND.get()) ? WAND_SPELLS : STAFF_SPELLS;
        SpellContainer container = stack.get(SpellDataComponents.SPELL_CONTAINER);
        List<String> bound = container == null ? List.of() : container.spell_ids().stream().map(pool::get).filter(Objects::nonNull).distinct().toList();
        return bound.isEmpty() ? pool.values().stream().distinct().sorted().toList() : bound;
    }

    /** Binds a spell as the choice screen does (for tests): it's added to the container and the choice is used up. */
    public static void bind(ItemStack stack, String spellId) {
        SpellContainer container = stack.getOrDefault(SpellDataComponents.SPELL_CONTAINER, SpellContainers.forMagicWeapon());
        stack.set(SpellDataComponents.SPELL_CONTAINER, new SpellContainer(container.access(), container.access_param(), container.pool(),
                container.slot(), container.max_spell_count(), List.of(spellId), container.extra_tier_binding()));
        stack.set(SpellDataComponents.SPELL_CHOICE, SpellChoice.EMPTY);
    }

    /** The pool a staff or wand offers its choice from, if it still has one to make. */
    public static String choicePool(ItemStack stack) {
        SpellChoice choice = stack.get(SpellDataComponents.SPELL_CHOICE);
        return choice == null ? "" : choice.pool();
    }
}
