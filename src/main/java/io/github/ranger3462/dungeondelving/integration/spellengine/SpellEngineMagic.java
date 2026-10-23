package io.github.ranger3462.dungeondelving.integration.spellengine;

import io.github.ranger3462.dungeondelving.skill.ActivitySkills;
import io.github.ranger3462.dungeondelving.skill.MagicSchool;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_power.api.SpellSchools;

/**
 * Spell Engine (the RPG Series' spells) training the magic school trees (DD-81). Only loaded when Spell Engine is
 * installed. Damage spells train their school when they hit something, by the damage done; other spells (heals,
 * buffs, teleports) train it when cast: {@link MagicSchool#CAST_XP} for a full cast, 1 per tick of a channel.
 */
public final class SpellEngineMagic {
    private SpellEngineMagic() {}

    /** Called from the mod constructor when Spell Engine is loaded. */
    public static void register() {
        SpellEvents.SPELL_CAST.register(args -> {
            if (!(args.caster() instanceof ServerPlayer player) || args.spell() == null) {
                return;
            }
            Spell spell = args.spell().value();
            if (spell.school == null || deals_damage(spell)) {
                return; // Damage spells train on their hits instead.
            }
            MagicSchool.byId(spell.school.id.toString()).ifPresent(school ->
                    ActivitySkills.award(player, school.skill, args.action() == SpellCast.Action.CHANNEL ? 1 : MagicSchool.CAST_XP));
        });
        NeoForge.EVENT_BUS.addListener(SpellEngineMagic::onDamage);
    }

    /** Whether a spell does damage (any of its impacts is a damage action). */
    private static boolean deals_damage(Spell spell) {
        return spell.impacts != null && spell.impacts.stream()
                .anyMatch(impact -> impact.action != null && impact.action.type == Spell.Impact.Action.Type.DAMAGE);
    }

    /** A player's spell damage trains the school whose damage type it is. */
    private static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0 || event.getEntity() == player) {
            return;
        }
        var type = event.getSource().typeHolder().unwrapKey();
        if (type.isEmpty()) {
            return;
        }
        for (var school : SpellSchools.all()) {
            if (type.get().equals(school.damageType)) {
                MagicSchool.byId(school.id.toString()).ifPresent(magic -> ActivitySkills.award(player, magic.skill, ActivitySkills.perHit(event.getNewDamage())));
                return;
            }
        }
    }
}
