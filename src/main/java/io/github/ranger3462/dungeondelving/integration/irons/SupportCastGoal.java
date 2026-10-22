package io.github.ranger3462.dungeondelving.integration.irons;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A caster that looks after its allies (or itself) first: it heals whoever is hurt worst, and keeps buffs up on allies
 * who are fighting, walking over to them if they're too far. Its spells are aimed straight at the ally, not wherever
 * the caster happens to be looking, so they never land on the player by mistake. Between casts it's free to attack.
 */
public class SupportCastGoal extends Goal {
    /** A buff spell, cast on an ally in a fight who doesn't have its effect yet. */
    public record Buff(AbstractSpell spell, Holder<MobEffect> effect) {}

    /** Heals an ally once it's below this share of its health. */
    private static final float HEAL_BELOW = 0.85f;
    /** How long after being hurt an ally still counts as fighting, in ticks. */
    private static final int FIGHT_MEMORY = 200;

    private final AbstractSpellCastingMob mob;
    private final Predicate<LivingEntity> allies;
    private final double searchRange;
    private final double castRange;
    private final int cooldownMin;
    private final int cooldownMax;
    private final List<AbstractSpell> heals;
    private final List<Buff> buffs;
    private float minQuality = 0.2f;
    private float maxQuality = 0.4f;

    /** The caster's tick count when it may next cast. */
    private int nextCast;
    private LivingEntity ally;
    private AbstractSpell spell;

    /**
     * @param allies who it supports (include the caster itself to let it heal and buff itself)
     * @param castRange how close it gets to an ally before casting (area spells like Fortify reach 8 blocks)
     * @param heals tried in order: if one can't be cast (e.g. no line of sight), the next is
     */
    public SupportCastGoal(AbstractSpellCastingMob mob, Predicate<LivingEntity> allies, double searchRange, double castRange, int cooldownMin,
            int cooldownMax, List<AbstractSpell> heals, List<Buff> buffs) {
        this.mob = mob;
        this.allies = allies;
        this.searchRange = searchRange;
        this.castRange = castRange;
        this.cooldownMin = cooldownMin;
        this.cooldownMax = cooldownMax;
        this.heals = heals;
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
        Optional<LivingEntity> hurt = candidates.stream().filter(entity -> entity.getHealth() < entity.getMaxHealth() * HEAL_BELOW)
                .min(Comparator.comparingDouble(entity -> entity.getHealth() / entity.getMaxHealth()));
        if (hurt.isPresent() && !heals.isEmpty()) {
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
        return (entity instanceof Mob other && other.getTarget() != null) || entity.tickCount - entity.getLastHurtByMobTimestamp() < FIGHT_MEMORY;
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
            mob.getLookControl().setLookAt(ally, 180.0f, 180.0f);
        }
        if (spell != null) {
            cast(spell);
        } else {
            for (AbstractSpell heal : heals) {
                if (cast(heal)) {
                    break;
                }
            }
        }
        nextCast = mob.tickCount + cooldownMin + mob.getRandom().nextInt(Math.max(1, cooldownMax - cooldownMin + 1));
        ally = null;
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
    public void stop() {
        ally = null;
        spell = null;
    }
}
