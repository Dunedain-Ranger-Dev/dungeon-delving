package io.github.ranger3462.dungeondelving.skill;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.character.MobLevels;
import io.github.ranger3462.dungeondelving.character.SkillLevels;
import io.github.ranger3462.dungeondelving.forging.ForgedGear;
import io.github.ranger3462.dungeondelving.integration.bettercombat.BetterCombatWeapons;
import io.github.ranger3462.dungeondelving.integration.skills.PufferfishSkills;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/**
 * The skills that level from what a player does in a fight or with animals (DD-81), each its own Pufferfish's Skills
 * tree: a combat style per kind of weapon, Dual Wielding, Blocking, a tree per armor weight, and Beastmastery. Magic
 * schools level from spellcasting, in the Iron's Spells and Spell Engine integrations ({@link MagicSchool}).
 * Without Pufferfish's Skills these do nothing.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class ActivitySkills {
    public static final String ONE_HANDED = "one_handed";
    public static final String TWO_HANDED = "two_handed";
    public static final String POLEARMS = "polearms";
    public static final String DAGGERS = "daggers";
    public static final String UNARMED = "unarmed";
    public static final String ARCHERY = "archery";
    public static final String CROSSBOWS = "crossbows";
    public static final String DUAL_WIELDING = "dual_wielding";
    public static final String BLOCKING = "blocking";
    public static final String BEASTMASTERY = "beastmastery";
    /** Most experience one hit (dealt, blocked or taken) can give. */
    public static final int MAX_PER_HIT = 20;
    public static final int TAME_XP = 40;
    public static final int BREED_XP = 10;

    /** Armor weights, each with its own tree. */
    public enum ArmorWeight {
        LIGHT, MEDIUM, HEAVY, MAGE;

        public final String skill = name().toLowerCase() + "_armor";
        /** Items in this tag count as this weight, whatever they're made of. */
        public final TagKey<Item> tag = TagKey.create(Registries.ITEM, DungeonDelving.id(skill));
    }

    private ActivitySkills() {}

    /** Gives a player experience in one of these skills. */
    public static void award(ServerPlayer player, String skill, int amount) {
        if (amount > 0 && SkillLevels.pufferfish()) {
            PufferfishSkills.addTotal(player, DungeonDelving.id(skill), amount);
        }
    }

    /**
     * Gives a player experience earned against a mob (hitting it, blocking it, being hit by it), scaled down when the
     * mob is well below the skill's level ({@link MobLevels#xpMultiplier}). A fraction left over gives one more point
     * by chance, so a trickle stays a trickle instead of rounding to nothing or to a whole point every time.
     */
    public static void awardAgainst(ServerPlayer player, String skill, int amount, Entity opponent) {
        if (amount <= 0 || !SkillLevels.pufferfish()) {
            return;
        }
        double scaled = amount;
        if (opponent instanceof Mob mob) {
            int skillLevel = PufferfishSkills.level(player, DungeonDelving.id(skill)).orElse(0);
            scaled = amount * MobLevels.xpMultiplier(skillLevel, MobLevels.level(mob));
        }
        int whole = (int) Math.floor(scaled);
        if (player.getRandom().nextDouble() < scaled - whole) {
            whole++;
        }
        award(player, skill, whole);
    }

    /** Experience for one hit of this much damage (dealt, blocked or taken): 1 per point, at most {@link #MAX_PER_HIT}. */
    public static int perHit(float amount) {
        return Mth.clamp(Math.round(amount), 1, MAX_PER_HIT);
    }

    /**
     * Which combat style a weapon trains: by its Better Combat weapon type when it has one, otherwise by what kind of
     * item it is. Empty for things that aren't weapons (a shield, a block, a staff).
     */
    public static Optional<String> styleFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.of(UNARMED);
        }
        if (ModList.get().isLoaded("bettercombat")) {
            Optional<String> category = BetterCombatWeapons.category(stack);
            if (category.isPresent()) {
                return styleForCategory(category.get());
            }
        }
        Item item = stack.getItem();
        if (item instanceof TridentItem) {
            return Optional.of(POLEARMS);
        }
        if (item instanceof SwordItem || item instanceof AxeItem || item instanceof MaceItem) {
            return Optional.of(ONE_HANDED);
        }
        return Optional.empty();
    }

    /** The combat style for a Better Combat weapon category. */
    public static Optional<String> styleForCategory(String category) {
        return Optional.ofNullable(switch (category) {
            case "sword", "rapier", "cutlass", "katana", "axe", "mace", "vanilla_mace", "coral_blade", "anchor" -> ONE_HANDED;
            case "claymore", "hammer", "heavy_axe", "double_axe", "battlestaff" -> TWO_HANDED;
            case "spear", "halberd", "glaive", "lance", "scythe", "trident" -> POLEARMS;
            case "dagger", "sickle", "claw", "soul_knife", "twin_blade" -> DAGGERS;
            case "fist" -> UNARMED;
            default -> null; // staffs, wands, pickaxes and anything unknown train no combat style
        });
    }

    /** Whether a stack is a weapon (for dual wielding): something that trains a combat style, not an empty hand or a shield. */
    private static boolean isWeapon(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof ShieldItem) && styleFor(stack).filter(style -> !style.equals(UNARMED)).isPresent();
    }

    /** The weight of an armor piece, or empty if it isn't armor. */
    public static Optional<ArmorWeight> weightOf(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem armor)) {
            return Optional.empty();
        }
        for (ArmorWeight weight : ArmorWeight.values()) {
            if (stack.is(weight.tag)) {
                return Optional.of(weight);
            }
        }
        if (armor instanceof ForgedGear.Armor forged) {
            return Optional.of(switch (forged.weight) {
                case LIGHT -> ArmorWeight.LIGHT;
                case MEDIUM -> ArmorWeight.MEDIUM;
                case HEAVY -> ArmorWeight.HEAVY;
                case GILDED -> ArmorWeight.MEDIUM;
            });
        }
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        // Spellcasting gear: robes from the RPG Series, and every Iron's Spells armor.
        if (id.getPath().contains("robe") || id.getNamespace().equals("irons_spellbooks")) {
            return Optional.of(ArmorWeight.MAGE);
        }
        var material = armor.getMaterial();
        if (material.is(ArmorMaterials.LEATHER)) {
            return Optional.of(ArmorWeight.LIGHT);
        }
        if (material.is(ArmorMaterials.CHAIN) || material.is(ArmorMaterials.GOLD)) {
            return Optional.of(ArmorWeight.MEDIUM);
        }
        if (material.is(ArmorMaterials.IRON) || material.is(ArmorMaterials.DIAMOND) || material.is(ArmorMaterials.NETHERITE)
                || material.is(ArmorMaterials.TURTLE)) {
            return Optional.of(ArmorWeight.HEAVY);
        }
        // Anything else by how protective its chestplate-equivalent is.
        int defense = armor.getDefense();
        return Optional.of(armor.getToughness() >= 2 || defense >= 6 ? ArmorWeight.HEAVY : defense >= 4 ? ArmorWeight.MEDIUM : ArmorWeight.LIGHT);
    }

    /** The armor weight a player is mostly wearing: at least two pieces of it (the heavier on a tie). Empty if none. */
    public static Optional<ArmorWeight> wornWeight(LivingEntity entity) {
        Map<ArmorWeight, Integer> counts = new EnumMap<>(ArmorWeight.class);
        for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            weightOf(entity.getItemBySlot(slot)).ifPresent(weight -> counts.merge(weight, 1, Integer::sum));
        }
        ArmorWeight best = null;
        for (ArmorWeight weight : ArmorWeight.values()) {
            int count = counts.getOrDefault(weight, 0);
            if (count >= 2 && (best == null || count >= counts.get(best))) {
                best = weight;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Hitting something trains the weapon's style (and Dual Wielding, with a weapon in each hand); taking a hit trains the armor worn. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        float amount = event.getNewDamage();
        if (amount <= 0 || event.getEntity() instanceof ArmorStand) {
            return;
        }
        var source = event.getSource();
        if (source.getEntity() instanceof ServerPlayer player && player != event.getEntity()) {
            if (source.getDirectEntity() == player) {
                LivingEntity target = event.getEntity();
                styleFor(player.getMainHandItem()).ifPresent(style -> awardAgainst(player, style, perHit(amount), target));
                if (isWeapon(player.getMainHandItem()) && isWeapon(player.getOffhandItem())) {
                    awardAgainst(player, DUAL_WIELDING, Math.max(1, perHit(amount) / 2), target);
                }
            } else if (source.getDirectEntity() instanceof ThrownTrident) {
                awardAgainst(player, POLEARMS, perHit(amount), event.getEntity());
            } else if (source.getDirectEntity() instanceof AbstractArrow arrow) {
                ItemStack weapon = arrow.getWeaponItem();
                awardAgainst(player, weapon != null && weapon.getItem() instanceof CrossbowItem ? CROSSBOWS : ARCHERY, perHit(amount), event.getEntity());
            }
        }
        // Armor only trains against real attackers, not falls, fire or cactus.
        if (event.getEntity() instanceof ServerPlayer player && source.getEntity() instanceof LivingEntity attacker && attacker != player) {
            wornWeight(player).ifPresent(weight -> awardAgainst(player, weight.skill, perHit(amount), attacker));
        }
    }

    @SubscribeEvent
    public static void onBlock(LivingShieldBlockEvent event) {
        if (event.getBlocked() && event.getEntity() instanceof ServerPlayer player && event.getBlockedDamage() > 0) {
            awardAgainst(player, BLOCKING, perHit(event.getBlockedDamage()), event.getDamageSource().getEntity());
        }
    }

    @SubscribeEvent
    public static void onTame(AnimalTameEvent event) {
        if (event.getTamer() instanceof ServerPlayer player) {
            award(player, BEASTMASTERY, TAME_XP);
        }
    }

    @SubscribeEvent
    public static void onBreed(BabyEntitySpawnEvent event) {
        if (event.getCausedByPlayer() instanceof ServerPlayer player) {
            award(player, BEASTMASTERY, BREED_XP);
        }
    }
}
