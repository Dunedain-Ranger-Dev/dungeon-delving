package io.github.ranger3462.dungeondelving.integration.irons;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import io.github.ranger3462.dungeondelving.entity.Mending;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A caster that looks after its allies (or itself) first: it mends whoever is hurt worst with {@link Mending} (never an
 * instant heal, so nothing is topped up to full mid-fight; a player only in a fight, as they heal on their own), and
 * keeps buffs up on allies who are fighting, walking over to them if they're too far. Its spells are aimed straight at the ally, not wherever
 * the caster happens to be looking, so they never land on the player by mistake. Between casts it's free to attack.
 */
public class SupportCastGoal extends Goal {
    /** A buff spell, cast on an ally in a fight who doesn't have its effect yet. */
    public record Buff(AbstractSpell spell, Holder<MobEffect> effect) {}

    /** How it mends a hurt ally: {@link Mending} at this amplifier (0 heals at Regeneration I's pace) for this many ticks. */
    public record Mend(int amplifier, int duration) {}

    /** Mends an ally once it's below this share of its health. */
    private static final float HEAL_BELOW = 0.85f;
    /** How long after being hurt an ally still counts as fighting, in ticks. */
    private static final int FIGHT_MEMORY = 200;
    /** Ticks spent turning to face an ally before casting, since aimed spells like Blessing of Life need to see them. */
    private static final int AIM_TICKS = 10;

    private final AbstractSpellCastingMob mob;
    private final Predicate<LivingEntity> allies;
    private final double searchRange;
    private final double castRange;
    private final int cooldownMin;
    private final int cooldownMax;
    private final Mend mend;
    private final List<Buff> buffs;
    private float minQuality = 0.2f;
    private float maxQuality = 0.4f;

    /** The caster's tick count when it may next cast. */
    private int nextCast;
    private LivingEntity ally;
    private AbstractSpell spell;
    private int aimed;

    /**
     * @param allies who it supports (include the caster itself to let it heal and buff itself)
     * @param castRange how close it gets to an ally before casting (area spells like Fortify reach 8 blocks)
     * @param mend how it mends hurt allies, or null if it doesn't
     */
    public SupportCastGoal(AbstractSpellCastingMob mob, Predicate<LivingEntity> allies, double searchRange, double castRange, int cooldownMin,
            int cooldownMax, Mend mend, List<Buff> buffs) {
        this.mob = mob;
        this.allies = allies;
        this.searchRange = searchRange;
        this.castRange = castRange;
        this.cooldownMin = cooldownMin;
        this.cooldownMax = cooldownMax;
        this.mend = mend;
        this.buffs = buffs;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public SupportCastGoal setSpellQuality(float min, float max) {
        minQuality = min;
        maxQuality = max;
        return this;
    }

    @Override
    public boolean canUse() {
        if (mob.tickCount < nextCast || mob.isCasting()) {
            return false;
        }
        List<LivingEntity> candidates = mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(searchRange),
                entity -> entity.isAlive() && allies.test(entity));
        Optional<LivingEntity> hurt = candidates.stream()
                .filter(entity -> entity.getHealth() < entity.getMaxHealth() * HEAL_BELOW && !entity.hasEffect(Mending.MENDING)
                        && (!(entity instanceof net.minecraft.world.entity.player.Player) || fighting(entity) || fighting(mob)))
                .min(Comparator.comparingDouble(entity -> entity.getHealth() / entity.getMaxHealth()));
        if (hurt.isPresent() && mend != null) {
            ally = hurt.get();
            spell = null;
            return true;
        }
        for (LivingEntity candidate : candidates) {
            if (!fighting(candidate)) {
                continue;
            }
            for (Buff buff : buffs) {
                if (!candidate.hasEffect(buff.effect())) {
                    ally = candidate;
                    spell = buff.spell();
                    return true;
                }
            }
        }
        return false;
    }

    private boolean fighting(LivingEntity entity) {
        // Hurt by a mob lately (not just new: the hurt timestamp starts at 0, so tickCount alone would call a fresh mob fighting).
        return (entity instanceof Mob other && other.getTarget() != null)
                || entity.getLastHurtByMob() != null && entity.tickCount - entity.getLastHurtByMobTimestamp() < FIGHT_MEMORY;
    }

    @Override
    public boolean canContinueToUse() {
        return ally != null && ally.isAlive() && !mob.isCasting() && mob.distanceToSqr(ally) < searchRange * searchRange;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (ally == null) {
            return; // Already cast; the goal stops on its next check.
        }
        if (ally != mob && mob.distanceToSqr(ally) > castRange * castRange) {
            mob.getNavigation().moveTo(ally, 1.2);
            mob.getLookControl().setLookAt(ally);
            return;
        }
        mob.getNavigation().stop();
        if (ally != mob) {
            face(ally);
            if (++aimed < AIM_TICKS) {
                return;
            }
        }
        if (spell != null) {
            cast(spell);
        } else {
            mend(ally);
        }
        nextCast = mob.tickCount + cooldownMin + mob.getRandom().nextInt(Math.max(1, cooldownMax - cooldownMin + 1));
        ally = null;
    }

    /**
     * Turns the caster's whole body to face an entity. Aimed spells look along the body, not the head, which is all a
     * mob's look control turns.
     */
    private void face(LivingEntity target) {
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        double dy = target.getEyeY() - mob.getEyeY();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setXRot(pitch);
    }

    /** Gives the ally Mending (slow healing, like Regeneration but for the undead too), with a burst of hearts so the player can see who's being mended. */
    private void mend(LivingEntity target) {
        target.addEffect(new MobEffectInstance(Mending.MENDING, mend.duration(), mend.amplifier()), mob);
        mob.swing(InteractionHand.MAIN_HAND);
        mob.playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 1.2f);
        if (mob.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(), 4, 0.4, 0.2, 0.4, 0.0);
        }
    }

    /** Starts casting a spell at the ally. False if the spell couldn't be cast. */
    private boolean cast(AbstractSpell toCast) {
        int level = Math.max(1, (int) (toCast.getMaxLevel() * Mth.lerp(mob.getRandom().nextFloat(), minQuality, maxQuality)));
        mob.initiateCastSpell(toCast, level);
        if (!mob.isCasting()) {
            return false;
        }
        // Aim it at the ally, whatever the caster's own aim found.
        MagicData.getPlayerMagicData(mob).setAdditionalCastData(new TargetEntityCastData(ally));
        return true;
    }

    @Override
    public void start() {
        aimed = 0;
    }

    @Override
    public void stop() {
        ally = null;
        spell = null;
    }
}
