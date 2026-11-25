package io.github.ranger3462.dungeondelving.integration.irons;

import java.util.List;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The Undead Dwarf Archivist, a tier 1 undead boss (DD-58): a dead dwarf scholar, short, hardy and well armored, who
 * waits at his ledgers while players work his puzzle ({@code PuzzleBosses}). Awake, he throws Magic Missiles, Stomps
 * and Slows, and Shields himself when pressed. Casts real Iron's Spells spells, so he only exists when Iron's Spells is
 * installed. Drawn with Iron's cultist look, at dwarf size, until he has his own.
 */
public class DwarfArchivist extends AbstractSpellCastingMob implements Enemy {
    public static final double MAX_HEALTH = 120.0;
    public static final double ARMOR = 12.0;

    public DwarfArchivist(EntityType<? extends DwarfArchivist> type, Level level) {
        super(type, level);
        xpReward = 50;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.SCALE, 0.85);
    }

    /** The Archivist, named, ready to be added to the world. He starts waiting ({@code PuzzleBosses#setUp}). */
    public static Mob create(ServerLevel level, BlockPos pos, int tier) {
        DwarfArchivist archivist = IronsSkeletons.DWARF_ARCHIVIST.get().create(level);
        archivist.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180.0f, 0.0f);
        EventHooks.finalizeMobSpawn(archivist, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        archivist.setCustomName(Component.translatable("entity.dungeondelving.undead_dwarf_archivist").withStyle(ChatFormatting.GOLD));
        archivist.setCustomNameVisible(true);
        return archivist;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new WizardAttackGoal(this, 1.0, 30, 55)
                .setSpells(List.of(SpellRegistry.MAGIC_MISSILE_SPELL.get(), SpellRegistry.STOMP_SPELL.get(), SpellRegistry.SLOW_SPELL.get()),
                        List.of(SpellRegistry.SHIELD_SPELL.get()), List.of(), List.of())
                .setSpellQuality(0.35f, 0.55f));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** He and the other undead never fight each other. */
    @Override
    public boolean isAlliedTo(Entity entity) {
        return super.isAlliedTo(entity) || entity.getType().is(EntityTypeTags.UNDEAD);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }
}
