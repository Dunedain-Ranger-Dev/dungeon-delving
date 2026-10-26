package io.github.ranger3462.dungeondelving.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.minecraft.world.item.armortrim.TrimMaterials;
import net.minecraft.world.item.armortrim.TrimPatterns;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;

/**
 * A town guard (DD-98): a human soldier who turns out when the town's raided, in place of iron golems. It fights the
 * raiders (and anything hostile), never the town's side.
 * <p>
 * Its look is the town's to set ({@code /realm guards}): a skin ({@link #pickSkin}: by default one of our guard set,
 * {@code textures/entity/town_guard/guard_<n>.png}, at random; or the game's built-in player faces; or one named skin,
 * which a resource pack can add to that folder), and a color, which dyes leather, trims metal armor and paints shields.
 * <p>
 * A town kits its guards out by its stats ({@link #equip}): better armor and weapons for a wealthier town, tougher and
 * harder-hitting guards for a better-defended one. How many turn out goes by morale (see {@code RealmLiveRaids}). Guards
 * aren't saved with the world: they're there for a fight, and gone when it's over or its ground unloads.
 */
public class TownGuard extends PathfinderMob {
    /** The game's built-in player skins (textures/entity/player/wide/), for towns whose guards wear a mix of faces. */
    public static final String[] BUILT_IN_SKINS = { "steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri" };
    /** How many skins our guard set has: textures/entity/town_guard/guard_1.png and up. */
    public static final int GUARD_SKINS = 8;
    /** A town's skin setting for our guard set (the default), and for the built-in faces. */
    public static final String SKIN_SET = "set";
    public static final String SKIN_MIXED = "mixed";
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(TownGuard.class, EntityDataSerializers.STRING);

    public TownGuard(EntityType<? extends TownGuard> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        if (!level.isClientSide) {
            entityData.set(SKIN, pickSkin(SKIN_SET, level.getRandom()));
        }
    }

    /**
     * A guard's skin for a town's setting: one of our set at random ({@code set} or blank), one of the built-in faces at
     * random ({@code mixed}, as {@code minecraft:<name>}), or that one named skin from our folder.
     */
    public static String pickSkin(String setting, net.minecraft.util.RandomSource random) {
        if (setting.isEmpty() || SKIN_SET.equals(setting)) {
            return "guard_" + (1 + random.nextInt(GUARD_SKINS));
        }
        if (SKIN_MIXED.equals(setting)) {
            return "minecraft:" + BUILT_IN_SKINS[random.nextInt(BUILT_IN_SKINS.length)];
        }
        return setting;
    }

    public void setSkin(String skin) {
        entityData.set(SKIN, skin);
    }

    public String skinName() {
        return entityData.get(SKIN);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.ATTACK_DAMAGE, 2.0).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.ATTACK_KNOCKBACK, 0.4);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN, "guard_1");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false, mob -> mob instanceof Enemy));
    }

    /** The skin's texture: a built-in face, or one in our town_guard folder. */
    public ResourceLocation skin() {
        String skin = entityData.get(SKIN);
        if (skin.startsWith("minecraft:")) {
            return ResourceLocation.withDefaultNamespace("textures/entity/player/wide/" + skin.substring("minecraft:".length()) + ".png");
        }
        ResourceLocation texture = ResourceLocation.tryBuild(io.github.ranger3462.dungeondelving.DungeonDelving.MODID, "textures/entity/town_guard/" + skin + ".png");
        return texture != null ? texture : io.github.ranger3462.dungeondelving.DungeonDelving.id("textures/entity/town_guard/guard_1.png");
    }

    /** The armor tier a town's wealth buys: 0 leather (below 5), 1 chainmail (5-9), 2 iron (10-19), 3 diamond (20 and up). */
    public static int armorTier(int wealth) {
        return wealth >= 20 ? 3 : wealth >= 10 ? 2 : wealth >= 5 ? 1 : 0;
    }

    /** A guard's health for a town's defense: 20, plus 1 per point of defense, up to 60. */
    public static double health(int defense) {
        return Mth.clamp(20.0 + defense, 20.0, 60.0);
    }

    /** A guard's attack damage (before its weapon) for a town's defense: 1, plus 1 per 5 points, up to 7. */
    public static double attack(int defense) {
        return Mth.clamp(1.0 + defense / 5, 1.0, 7.0);
    }

    /**
     * Kits a guard out for its town: armor and weapon by wealth, health and attack by defense, and the town's color, if
     * it has one: leather dyed, metal armor trimmed, the shield painted.
     */
    public void equip(int wealth, int defense, java.util.Optional<DyeColor> color) {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(health(defense));
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attack(defense));
        setHealth(getMaxHealth());
        Item[][] sets = {
                { Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS },
                { Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS },
                { Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS },
                { Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS } };
        Item[] weapons = { Items.STONE_SWORD, Items.IRON_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD };
        int tier = armorTier(wealth);
        EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
        for (int i = 0; i < slots.length; i++) {
            ItemStack piece = new ItemStack(sets[tier][i]);
            color.ifPresent(dye -> colorPiece(piece, dye));
            setItemSlot(slots[i], piece);
            setDropChance(slots[i], 0.0f);
        }
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapons[tier]));
        setDropChance(EquipmentSlot.MAINHAND, 0.0f);
        ItemStack shield = tier >= 1 ? new ItemStack(Items.SHIELD) : ItemStack.EMPTY;
        color.ifPresent(dye -> shield.set(DataComponents.BASE_COLOR, dye));
        setItemSlot(EquipmentSlot.OFFHAND, shield);
        setDropChance(EquipmentSlot.OFFHAND, 0.0f);
    }

    /** Leather is dyed the color; other armor gets a sentry trim in the nearest trim material. */
    private void colorPiece(ItemStack piece, DyeColor dye) {
        if (piece.is(net.minecraft.tags.ItemTags.DYEABLE)) {
            piece.set(DataComponents.DYED_COLOR, new DyedItemColor(dye.getTextureDiffuseColor(), false));
            return;
        }
        var materials = level().registryAccess().registryOrThrow(Registries.TRIM_MATERIAL);
        var patterns = level().registryAccess().registryOrThrow(Registries.TRIM_PATTERN);
        var material = materials.getHolder(trimMaterial(dye));
        var pattern = patterns.getHolder(TrimPatterns.SENTRY);
        if (material.isPresent() && pattern.isPresent()) {
            piece.set(DataComponents.TRIM, new ArmorTrim(material.get(), pattern.get(), false));
        }
    }

    /** The armor trim material nearest a dye color. */
    public static net.minecraft.resources.ResourceKey<TrimMaterial> trimMaterial(DyeColor dye) {
        return switch (dye) {
            case WHITE -> TrimMaterials.QUARTZ;
            case LIGHT_GRAY, GRAY -> TrimMaterials.IRON;
            case BLACK -> TrimMaterials.NETHERITE;
            case RED, PINK -> TrimMaterials.REDSTONE;
            case ORANGE, BROWN -> TrimMaterials.COPPER;
            case YELLOW -> TrimMaterials.GOLD;
            case LIME, GREEN -> TrimMaterials.EMERALD;
            case CYAN, LIGHT_BLUE -> TrimMaterials.DIAMOND;
            case BLUE -> TrimMaterials.LAPIS;
            case PURPLE, MAGENTA -> TrimMaterials.AMETHYST;
        };
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        return super.doHurtTarget(target);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof TownGuard) && super.canAttack(target);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("skin", entityData.get(SKIN));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("skin", net.minecraft.nbt.Tag.TAG_STRING)) {
            entityData.set(SKIN, tag.getString("skin"));
        }
    }
}
