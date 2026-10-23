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
 * The Lich Lord, the tier 4 undead boss: a withered, husk-like sorcerer who moves at a skeleton's pace. He throws
 * Wither Skulls and Blood Slashes, raises the dead to fight for him, Blood Steps away when pressed, and now and then
 * mends himself. Casts real Iron's Spells spells, so he only exists when Iron's Spells is installed (otherwise the
 * Barrow King guards tier 4 too).
 */
public class LichLord extends AbstractSpellCastingMob implements Enemy {
    public static final double MAX_HEALTH = 260.0;

    public LichLord(EntityType<? extends LichLord> type, Level level) {
        super(type, level);
        xpReward = 80;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.SCALE, 1.4);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // No teleports or instant heals: he mends slowly (Mending) now and then instead.
        goalSelector.addGoal(1, new SupportCastGoal(this, self -> self == this, 1.0, 0.0, 240, 300, new SupportCastGoal.Mend(0, 160), List.of()));
        goalSelector.addGoal(2, new WizardAttackGoal(this, 1.25, 30, 60)
                .setSpells(List.of(SpellRegistry.WITHER_SKULL_SPELL.get(), SpellRegistry.BLOOD_SLASH_SPELL.get(), SpellRegistry.RAISE_DEAD_SPELL.get()),
                        List.of(), List.of(), List.of())
                .setSpellQuality(0.5f, 0.75f));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** The undead are his to command: he and they never fight each other. */
    @Override
    public boolean isAlliedTo(Entity entity) {
        return super.isAlliedTo(entity) || entity.getType().is(EntityTypeTags.UNDEAD);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.HUSK_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.HUSK_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    /** Makes the Lich Lord for a boss arena; {@code DungeonBosses} adds him to the world. */
    public static Mob create(ServerLevel level, BlockPos pos, int tier) {
        LichLord lich = IronsSkeletons.LICH_LORD.get().create(level);
        lich.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180.0f, 0.0f);
        EventHooks.finalizeMobSpawn(lich, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        lich.setCustomName(Component.translatable("entity.dungeondelving.lich_lord").withStyle(ChatFormatting.DARK_PURPLE));
        lich.setCustomNameVisible(true);
        return lich;
    }
}
