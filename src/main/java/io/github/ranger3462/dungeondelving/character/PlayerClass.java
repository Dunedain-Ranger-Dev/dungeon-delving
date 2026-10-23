package io.github.ranger3462.dungeondelving.character;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

/**
 * The classes (DD-83): Fighter, Archer, Rogue and Mage. Each has its own skill tree, split from the RPG Series' class
 * tree (Fighter: Warrior and Paladin; Archer; Rogue; Mage: Arcane, Fire, Frost and Priest), locked until the class
 * is picked, plus a small passive bonus. Magic schools stay open to every class (anyone can cast from a spell book or
 * staff); the Mage is simply better at it. Picked, or changed, with a Tome of Calling.
 */
public enum PlayerClass {
    FIGHTER("fighter",
            new Race.Trait("minecraft:generic.max_health", 2.0, Operation.ADD_VALUE),
            new Race.Trait("minecraft:generic.attack_damage", 0.05, Operation.ADD_MULTIPLIED_BASE)),
    ARCHER("archer",
            new Race.Trait("ranged_weapon:damage", 0.05, Operation.ADD_MULTIPLIED_TOTAL)),
    ROGUE("rogue",
            new Race.Trait("minecraft:generic.attack_speed", 0.05, Operation.ADD_MULTIPLIED_BASE),
            new Race.Trait("minecraft:generic.movement_speed", 0.03, Operation.ADD_MULTIPLIED_BASE)),
    MAGE("mage",
            new Race.Trait("spell_power:generic", 0.5, Operation.ADD_VALUE),
            new Race.Trait("irons_spellbooks:mana_regen", 0.1, Operation.ADD_MULTIPLIED_BASE),
            new Race.Trait("irons_spellbooks:max_mana", 20.0, Operation.ADD_VALUE));

    public final String id;
    /** The class's passive bonus (attributes from mods that aren't installed are skipped). */
    public final List<Race.Trait> traits;

    PlayerClass(String id, Race.Trait... traits) {
        this.id = id;
        this.traits = List.of(traits);
    }

    /** The class's skill tree (a Pufferfish's Skills category). */
    public ResourceLocation tree() {
        return DungeonDelving.id(id);
    }

    public Component displayName() {
        return Component.translatable("class.dungeondelving." + id);
    }

    public Component description() {
        return Component.translatable("class.dungeondelving." + id + ".description");
    }

    public static Optional<PlayerClass> byId(String id) {
        return Arrays.stream(values()).filter(c -> c.id.equals(id)).findFirst();
    }
}
