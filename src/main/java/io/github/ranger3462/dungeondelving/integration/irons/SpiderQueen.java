package io.github.ranger3462.dungeondelving.integration.irons;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;
import io.redspace.ironsspellbooks.entity.mobs.ice_spider.IceSpiderEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.Level;

/**
 * The spider queens (DD-58): Iron's Spells' ice spider as the spiders' bosses, one per tier. The Spider Queen (tier 1)
 * neither freezes nor casts; the Cave Spider Queen (tier 2) poisons what she grapples and casts poison in place of ice;
 * the Ice Spider Queen (tier 3) is a bigger, tougher ice spider. Each is drawn in its own colors ({@link SpiderQueenRenderer}).
 * One subclass per kind, not a field: goals are set up in the constructor, before fields are.
 */
public abstract class SpiderQueen extends IceSpiderEntity {
    private static List<Field> spellLists;

    protected SpiderQueen(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    /** Whether her grapple freezes, as an ice spider's does. */
    protected abstract boolean freezes();

    /** Whether her grapple poisons. */
    protected boolean poisons() {
        return false;
    }

    /** The spells she casts in place of the ice spider's ice spells; null to keep them. */
    protected abstract List<AbstractSpell> spells();

    @Override
    protected void registerGoals() {
        super.registerGoals();
        List<AbstractSpell> spells = spells();
        if (spells == null) {
            return;
        }
        for (WrappedGoal wrapped : goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof WizardAttackGoal goal) {
                replaceSpells(goal, spells);
            }
        }
    }

    /** Empties the goal's spell lists, then gives it these as its attack spells. Iron's keeps the lists protected. */
    @SuppressWarnings("unchecked")
    private static void replaceSpells(WizardAttackGoal goal, List<AbstractSpell> attack) {
        try {
            if (spellLists == null) {
                List<Field> fields = new ArrayList<>();
                for (Field field : WizardAttackGoal.class.getDeclaredFields()) {
                    if (field.getType() == ArrayList.class && field.getName().endsWith("Spells")) {
                        field.setAccessible(true);
                        fields.add(field);
                    }
                }
                spellLists = fields;
            }
            for (Field field : spellLists) {
                ArrayList<AbstractSpell> list = (ArrayList<AbstractSpell>) field.get(goal);
                list.clear();
                if (field.getName().equals("attackSpells")) {
                    list.addAll(attack);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            DungeonDelving.LOGGER.warn("Couldn't change a spider queen's spells; she'll cast the ice spider's", e);
        }
    }

    /** The attack spells she casts (for tests). */
    @SuppressWarnings("unchecked")
    public List<AbstractSpell> attackSpells() {
        for (WrappedGoal wrapped : goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof WizardAttackGoal goal) {
                try {
                    Field field = WizardAttackGoal.class.getDeclaredField("attackSpells");
                    field.setAccessible(true);
                    return List.copyOf((ArrayList<AbstractSpell>) field.get(goal));
                } catch (ReflectiveOperationException e) {
                    return List.of();
                }
            }
        }
        return List.of();
    }

    /** Grapples like an ice spider, but undoes the freezing unless she {@link #freezes}, and poisons if she {@link #poisons}. */
    @Override
    public void tickGrapple() {
        UUID id = getGrappleTargetUUID();
        Entity target = id != null && level() instanceof ServerLevel server ? server.getEntity(id) : null;
        int frozen = target == null ? 0 : target.getTicksFrozen();
        super.tickGrapple();
        if (target == null) {
            return;
        }
        if (!freezes()) {
            target.setTicksFrozen(frozen);
        }
        if (poisons() && target instanceof LivingEntity living && !living.hasEffect(MobEffects.POISON)) {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), this);
        }
    }

    /** Tier 1: a spider-colored ice spider with no ice at all. */
    public static class Common extends SpiderQueen {
        public Common(EntityType<? extends PathfinderMob> type, Level level) {
            super(type, level);
        }

        @Override
        protected boolean freezes() {
            return false;
        }

        @Override
        protected List<AbstractSpell> spells() {
            return List.of();
        }
    }

    /** Tier 2: cave spider colors, poison in place of ice. */
    public static class Cave extends SpiderQueen {
        public Cave(EntityType<? extends PathfinderMob> type, Level level) {
            super(type, level);
        }

        @Override
        protected boolean freezes() {
            return false;
        }

        @Override
        protected boolean poisons() {
            return true;
        }

        @Override
        protected List<AbstractSpell> spells() {
            return List.of(SpellRegistry.POISON_ARROW_SPELL.get(), SpellRegistry.ACID_ORB_SPELL.get());
        }
    }

    /** Tier 3: the ice spider itself, only bigger and tougher. */
    public static class Ice extends SpiderQueen {
        public Ice(EntityType<? extends PathfinderMob> type, Level level) {
            super(type, level);
        }

        @Override
        protected boolean freezes() {
            return true;
        }

        @Override
        protected List<AbstractSpell> spells() {
            return null;
        }
    }
}
