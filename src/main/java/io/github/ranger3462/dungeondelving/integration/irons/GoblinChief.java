package io.github.ranger3462.dungeondelving.integration.irons;

import java.util.List;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Goblin Shaman Chief, the tier 1 goblin boss: a shaman who has taken over a goblin clan. Not very tough, but
 * dangerous: he keeps flinging fire, acid and poison, and heals himself whenever he's hurt, while the arena's spawners
 * pour out his goblins.
 */
public class GoblinChief extends GoblinShaman {
    public static final double MAX_HEALTH = 70.0;

    public GoblinChief(EntityType<? extends GoblinChief> type, Level level) {
        super(type, level);
        xpReward = 40;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return GoblinShaman.createAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // Mends himself (Mending) whenever he's hurt, even when nobody is fighting back properly (e.g. a creative player hitting him).
        goalSelector.addGoal(1, new SupportCastGoal(this, self -> self == this, 1.0, 0.0, 200, 260, new SupportCastGoal.Mend(0, 160),
                List.of()).setSpellQuality(0.3f, 0.5f));
        // Otherwise it's all damage.
        goalSelector.addGoal(2, new WizardAttackGoal(this, 1.2, 20, 40)
                .setSpells(
                        List.of(SpellRegistry.FIREBOLT_SPELL.get(), SpellRegistry.ACID_ORB_SPELL.get(), SpellRegistry.POISON_ARROW_SPELL.get()),
                        List.of(), List.of(), List.of())
                .setSpellQuality(0.35f, 0.55f));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }
}
