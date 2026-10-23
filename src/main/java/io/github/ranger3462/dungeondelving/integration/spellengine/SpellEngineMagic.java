package io.github.ranger3462.dungeondelving.integration.spellengine;

import io.github.ranger3462.dungeondelving.skill.ActivitySkills;
import io.github.ranger3462.dungeondelving.skill.MagicSchool;
import net.minecraft.server.level.ServerPlayer;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.internals.casting.SpellCast;

/**
 * Spell Engine (the RPG Series' spells) training the magic school trees (DD-81). Only loaded when Spell Engine is
 * installed. A full cast gives {@link MagicSchool#CAST_XP}; each tick of a channelled spell gives 1.
 */
public final class SpellEngineMagic {
    private SpellEngineMagic() {}

    /** Called from the mod constructor when Spell Engine is loaded. */
    public static void register() {
        SpellEvents.SPELL_CAST.register(args -> {
            if (!(args.caster() instanceof ServerPlayer player) || args.spell() == null || args.spell().value().school == null) {
                return;
            }
            MagicSchool.byId(args.spell().value().school.id.toString()).ifPresent(school ->
                    ActivitySkills.award(player, school.skill, args.action() == SpellCast.Action.CHANNEL ? 1 : MagicSchool.CAST_XP));
        });
    }
}
