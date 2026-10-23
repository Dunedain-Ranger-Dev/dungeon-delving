package io.github.ranger3462.dungeondelving.integration.irons;

import java.util.Set;

import io.github.ranger3462.dungeondelving.skill.ActivitySkills;
import io.github.ranger3462.dungeondelving.skill.MagicSchool;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Iron's Spells training the magic school trees (DD-81). Damage spells train their school when they hit something,
 * by the damage done; other spells (heals, buffs, teleports) train it when cast: {@link MagicSchool#CAST_XP} plus a
 * little for higher spell levels. Only loaded when Iron's Spells is installed.
 */
public final class IronsMagic {
    /** The spell info lines that mean a spell does damage (not "reduced damage" or "damage reduction"). */
    private static final Set<String> DAMAGE_INFO = Set.of("ui.irons_spellbooks.damage", "ui.irons_spellbooks.base_damage",
            "ui.irons_spellbooks.percent_damage", "ui.irons_spellbooks.impact_damage", "ui.irons_spellbooks.aoe_damage");

    private IronsMagic() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(IronsMagic::onCast);
        NeoForge.EVENT_BUS.addListener(IronsMagic::onDamage);
    }

    private static void onCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        AbstractSpell spell = SpellRegistry.getSpell(event.getSpellId());
        if (dealsDamage(spell, event.getSpellLevel(), player)) {
            return; // Damage spells train on their hits instead.
        }
        MagicSchool.byId(event.getSchoolType().getId().toString()).ifPresent(school ->
                ActivitySkills.award(player, school.skill, MagicSchool.CAST_XP + event.getSpellLevel() / 2));
    }

    /** Whether a spell does damage, going by the info it lists (Iron's shows a damage line for every damage spell). */
    public static boolean dealsDamage(AbstractSpell spell, int level, ServerPlayer caster) {
        for (Component line : spell.getUniqueInfo(level, caster)) {
            if (line.getContents() instanceof TranslatableContents translatable && DAMAGE_INFO.contains(translatable.getKey())) {
                return true;
            }
        }
        return false;
    }

    /** A player's spell damage trains its school. */
    private static void onDamage(LivingDamageEvent.Post event) {
        if (event.getSource() instanceof SpellDamageSource source && source.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0
                && event.getEntity() != player) {
            MagicSchool.byId(source.spell().getSchoolType().getId().toString()).ifPresent(school ->
                    ActivitySkills.award(player, school.skill, ActivitySkills.perHit(event.getNewDamage())));
        }
    }
}
