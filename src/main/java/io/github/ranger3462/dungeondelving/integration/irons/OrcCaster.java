package io.github.ranger3462.dungeondelving.integration.irons;

import java.util.List;

import io.github.ranger3462.dungeondelving.entity.OrcBosses;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;

/**
 * An orc spellcaster: casts real Iron's Spells spells, so orc casters only exist when Iron's Spells is installed. The
 * Doomsayer, Shaman and Lorekeeper differ in their spells and in how they help the orcs around them.
 */
public abstract class OrcCaster extends AbstractSpellCastingMob implements Enemy {
    protected OrcCaster(EntityType<? extends OrcCaster> type, Level level) {
        super(type, level);
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 26.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        addSupportGoal();
        goalSelector.addGoal(2, new WizardAttackGoal(this, 1.1, 40, 70).setSpells(attackSpells(), List.of(), movementSpells(), selfHeals())
                .setSpellQuality(spellQuality(), spellQuality() + 0.2f));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    protected abstract List<AbstractSpell> attackSpells();

    protected List<AbstractSpell> movementSpells() {
        return List.of();
    }

    protected List<AbstractSpell> selfHeals() {
        return List.of();
    }

    protected float spellQuality() {
        return 0.2f;
    }

    /** How it helps the orcs around it, if it does. */
    protected void addSupportGoal() {}

    protected static boolean isOrcAlly(Mob caster, LivingEntity ally) {
        return ally != caster && ally.getType().is(OrcBosses.ORCS);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PIGLIN_BRUTE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PIGLIN_BRUTE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PIGLIN_BRUTE_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.75f;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    /** The orc doomsayer (tiers 2-3): blood magic, and it keeps the warband Fortified. */
    public static class Doomsayer extends OrcCaster {
        public Doomsayer(EntityType<? extends Doomsayer> type, Level level) {
            super(type, level);
        }

        @Override
        protected List<AbstractSpell> attackSpells() {
            return List.of(SpellRegistry.BLOOD_NEEDLES_SPELL.get(), SpellRegistry.RAY_OF_SIPHONING_SPELL.get());
        }

        @Override
        protected void addSupportGoal() {
            goalSelector.addGoal(1, new SupportCastGoal(this, ally -> isOrcAlly(this, ally), 16.0, 8.0, 60, 120, List.of(),
                    List.of(new SupportCastGoal.Buff(SpellRegistry.FORTIFY_SPELL.get(), MobEffectRegistry.FORTIFY))).setSpellQuality(0.2f, 0.4f));
        }
    }

    /** The orc shaman (tier 4): calls down lightning and heals the warband. */
    public static class Shaman extends OrcCaster {
        public Shaman(EntityType<? extends Shaman> type, Level level) {
            super(type, level);
            xpReward = 14;
        }

        @Override
        protected List<AbstractSpell> attackSpells() {
            return List.of(SpellRegistry.LIGHTNING_BOLT_SPELL.get(), SpellRegistry.CHAIN_LIGHTNING_SPELL.get());
        }

        @Override
        protected List<AbstractSpell> selfHeals() {
            return List.of(SpellRegistry.HEAL_SPELL.get());
        }

        @Override
        protected float spellQuality() {
            return 0.3f;
        }

        @Override
        protected void addSupportGoal() {
            goalSelector.addGoal(1, new SupportCastGoal(this, ally -> isOrcAlly(this, ally), 16.0, 6.0, 40, 80,
                    List.of(SpellRegistry.BLESSING_OF_LIFE_SPELL.get()), List.of()).setSpellQuality(0.3f, 0.5f));
        }
    }

    /**
     * The Orc Lorekeeper, the tier 3 orc boss: keeper of the warband's old magic. Eldritch Blasts, Wither Skulls and Blood
     * Slashes, Blood Step out of reach, and Greater Heal when hurt.
     */
    public static class Lorekeeper extends OrcCaster {
        public static final double MAX_HEALTH = 180.0;

        public Lorekeeper(EntityType<? extends Lorekeeper> type, Level level) {
            super(type, level);
            xpReward = 60;
        }

        public static AttributeSupplier.Builder createAttributes() {
            return OrcCaster.createAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH).add(Attributes.ARMOR, 6.0)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 0.7).add(Attributes.SCALE, 1.2).add(Attributes.FOLLOW_RANGE, 48.0);
        }

        @Override
        protected List<AbstractSpell> attackSpells() {
            return List.of(SpellRegistry.ELDRITCH_BLAST_SPELL.get(), SpellRegistry.WITHER_SKULL_SPELL.get(), SpellRegistry.BLOOD_SLASH_SPELL.get());
        }

        @Override
        protected List<AbstractSpell> movementSpells() {
            return List.of(SpellRegistry.BLOOD_STEP_SPELL.get());
        }

        @Override
        protected List<AbstractSpell> selfHeals() {
            return List.of(SpellRegistry.GREATER_HEAL_SPELL.get());
        }

        @Override
        protected float spellQuality() {
            return 0.5f;
        }

        public static Mob create(ServerLevel level, BlockPos pos, int tier) {
            Lorekeeper boss = IronsOrcs.ORC_LOREKEEPER.get().create(level);
            boss.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180.0f, 0.0f);
            EventHooks.finalizeMobSpawn(boss, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            boss.setCustomName(Component.translatable("entity.dungeondelving.orc_lorekeeper").withStyle(ChatFormatting.GOLD));
            boss.setCustomNameVisible(true);
            return boss;
        }
    }
}
