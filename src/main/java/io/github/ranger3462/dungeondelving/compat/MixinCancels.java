package io.github.ranger3462.dungeondelving.compat;

import java.util.List;
import java.util.Set;

import com.bawnorton.mixinsquared.api.MixinCanceller;

/**
 * Other mods' mixins this pack can't run together, cancelled through MixinSquared (registered in
 * META-INF/services). Each is a clash where both mods do the same thing, so nothing is lost.
 * <ul>
 * <li>Sensible Survival's anvil screen mixin: it lifts the anvil's "Too Expensive!" level cap, and so does Apothic
 * Enchanting's. Mixin lets only one change that number; the other, being required, crashed the game whenever it
 * loaded second.</li>
 * </ul>
 */
public final class MixinCancels implements MixinCanceller {
    private static final Set<String> CANCELLED = Set.of("net.sensible_survival.mixin.client.AnvilScreenMixin");

    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        return CANCELLED.contains(mixinClassName);
    }
}
