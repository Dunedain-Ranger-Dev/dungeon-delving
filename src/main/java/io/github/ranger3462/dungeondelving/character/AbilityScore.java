package io.github.ranger3462.dungeondelving.character;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The six ability scores (DD-80), D&D-style: real attributes, so gear, races and skill trees can raise them the usual
 * way. Everyone starts at {@link #BASE}; each point above it (or below) adds to other attributes through
 * {@link Effect}s, so the RPG Series, Better Combat and Iron's Spells all feel it. Attributes from mods that aren't
 * installed are skipped.
 */
public enum AbilityScore {
    STRENGTH("strength", "STR",
            new Effect("minecraft:generic.attack_damage", 0.02, Operation.ADD_MULTIPLIED_BASE),
            new Effect("minecraft:generic.knockback_resistance", 0.01, Operation.ADD_VALUE)),
    DEXTERITY("dexterity", "DEX",
            new Effect("minecraft:generic.attack_speed", 0.01, Operation.ADD_MULTIPLIED_BASE),
            new Effect("ranged_weapon:damage", 0.02, Operation.ADD_MULTIPLIED_TOTAL),
            new Effect("minecraft:generic.movement_speed", 0.003, Operation.ADD_MULTIPLIED_BASE)),
    CONSTITUTION("constitution", "CON",
            new Effect("minecraft:generic.max_health", 1.0, Operation.ADD_VALUE),
            new Effect("minecraft:generic.armor_toughness", 0.1, Operation.ADD_VALUE)),
    INTELLIGENCE("intelligence", "INT",
            new Effect("spell_power:arcane", 0.25, Operation.ADD_VALUE),
            new Effect("spell_power:fire", 0.25, Operation.ADD_VALUE),
            new Effect("spell_power:frost", 0.25, Operation.ADD_VALUE),
            new Effect("irons_spellbooks:spell_power", 0.01, Operation.ADD_MULTIPLIED_BASE),
            new Effect("irons_spellbooks:max_mana", 5.0, Operation.ADD_VALUE)),
    WISDOM("wisdom", "WIS",
            new Effect("spell_power:healing", 0.25, Operation.ADD_VALUE),
            new Effect("spell_power:holy", 0.25, Operation.ADD_VALUE),
            new Effect("irons_spellbooks:mana_regen", 0.02, Operation.ADD_MULTIPLIED_BASE)),
    /** For now just luck; trading prices, faction standing and companions come later. */
    CHARISMA("charisma", "CHA",
            new Effect("minecraft:generic.luck", 0.1, Operation.ADD_VALUE));

    /** Where every score starts, before points, race and gear. */
    public static final double BASE = 10.0;
    public static final double MIN = 1.0;
    public static final double MAX = 40.0;

    /** In its own class, since an enum's constructor can't use the enum's own static fields. */
    private static final class Registry {
        static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, DungeonDelving.MODID);
    }

    /** What each point of a score above {@link #BASE} adds to another attribute, by its id. */
    public record Effect(String attribute, double perPoint, Operation operation) {}

    public final String id;
    /** The short name shown on the stats screen. */
    public final String abbreviation;
    public final List<Effect> effects;
    private final DeferredHolder<Attribute, Attribute> attribute;

    AbilityScore(String id, String abbreviation, Effect... effects) {
        this.id = id;
        this.abbreviation = abbreviation;
        this.effects = List.of(effects);
        this.attribute = Registry.ATTRIBUTES.register(id, () -> new RangedAttribute("attribute.dungeondelving." + id, BASE, MIN, MAX).setSyncable(true));
    }

    public DeferredHolder<Attribute, Attribute> attribute() {
        return attribute;
    }

    public Component displayName() {
        return Component.translatable("attribute.dungeondelving." + id);
    }

    /** Where this score's effect on an attribute is kept on the player. */
    ResourceLocation effectId() {
        return DungeonDelving.id("score/" + id);
    }

    public static Optional<AbilityScore> byId(String id) {
        return Arrays.stream(values()).filter(score -> score.id.equals(id)).findFirst();
    }

    public static void register(IEventBus modEventBus) {
        Registry.ATTRIBUTES.register(modEventBus);
        modEventBus.addListener((EntityAttributeModificationEvent event) -> {
            for (AbilityScore score : values()) {
                event.add(EntityType.PLAYER, score.attribute);
            }
        });
    }
}
