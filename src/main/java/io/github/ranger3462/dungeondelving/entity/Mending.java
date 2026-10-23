package io.github.ranger3462.dungeondelving.entity;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Mending: what dungeon casters give hurt allies instead of an instant heal. Heals slowly, like Regeneration, but
 * works on the undead too (vanilla Regeneration doesn't), so a skeleton mage can mend the Barrow King.
 */
public class Mending extends MobEffect {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, DungeonDelving.MODID);
    public static final DeferredHolder<MobEffect, Mending> MENDING = EFFECTS.register("mending", Mending::new);
    /** Ticks between each point of healing at amplifier 0 (Regeneration I's pace); each level halves it. */
    public static final int INTERVAL = 50;

    private Mending() {
        super(MobEffectCategory.BENEFICIAL, 0xE8A0C8);
    }

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(1.0f);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = INTERVAL >> amplifier;
        return interval <= 0 || duration % interval == 0;
    }
}
