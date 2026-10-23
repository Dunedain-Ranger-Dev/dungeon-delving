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
 * installed. Each spell trains its school by what it actually does:
 * <ul>
 * <li>damage spells by the damage they deal, less against mobs well below the school's level;</li>
 * <li>healing spells by the health they actually restore (nothing for healing someone at full health);</li>
 * <li>anything else (buffs, shields, teleports) when cast, at most once per {@link MagicSchool#CAST_XP_COOLDOWN_TICKS} per
 * spell.</li>
 * </ul>
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
            if (spell.school == null || has(spell, Spell.Impact.Action.Type.DAMAGE) || has(spell, Spell.Impact.Action.Type.HEAL)) {
                return; // Damage and healing spells train on what they do instead.
            }
            String id = args.spell().unwrapKey().map(key -> key.location().toString()).orElse(spell.school.id.toString());
            MagicSchool.byId(spell.school.id.toString()).ifPresent(school ->
                    school.awardCast(player, id, args.action() == SpellCast.Action.CHANNEL ? 1 : MagicSchool.CAST_XP));
        });
        SpellEvents.HEAL.register(args -> {
            if (args.caster() instanceof ServerPlayer player && args.spell() != null && args.spell().value().school != null && args.target() != null) {
                float restored = Math.min(args.amount(), args.target().getMaxHealth() - args.target().getHealth());
                if (restored > 0) {
                    MagicSchool.byId(args.spell().value().school.id.toString()).ifPresent(school ->
                            ActivitySkills.award(player, school.skill, ActivitySkills.perHit(restored)));
                }
            }
        });
        NeoForge.EVENT_BUS.addListener(SpellEngineMagic::onDamage);
    }

    /** Whether any of a spell's impacts is this kind of action. */
    private static boolean has(Spell spell, Spell.Impact.Action.Type type) {
        return spell.impacts != null && spell.impacts.stream().anyMatch(impact -> impact.action != null && impact.action.type == type);
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
                MagicSchool.byId(school.id.toString()).ifPresent(magic ->
                        ActivitySkills.awardAgainst(player, magic.skill, ActivitySkills.perHit(event.getNewDamage()), event.getEntity()));
                return;
            }
        }
    }
}
