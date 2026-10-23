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
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

/**
 * Iron's Spells training the magic school trees (DD-81). Only loaded when Iron's Spells is installed. Each spell
 * trains its school by what it actually does:
 * <ul>
 * <li>damage spells by the damage they deal, less against mobs well below the school's level;</li>
 * <li>healing spells by the health they actually restore (Iron's heals don't say who cast them, so heals near a player
 * who just cast one are credited to them; see {@link MagicSchool#onHealed});</li>
 * <li>anything else (buffs, shields, teleports) when cast, at most once per {@link MagicSchool#CAST_XP_COOLDOWN_TICKS} per
 * spell.</li>
 * </ul>
 */
public final class IronsMagic {
    /** The spell info lines that mean a spell does damage (not "reduced damage" or "damage reduction"). */
    private static final Set<String> DAMAGE_INFO = Set.of("ui.irons_spellbooks.damage", "ui.irons_spellbooks.base_damage",
            "ui.irons_spellbooks.percent_damage", "ui.irons_spellbooks.impact_damage", "ui.irons_spellbooks.aoe_damage");
    /** The spell info lines that mean a spell heals. */
    private static final Set<String> HEALING_INFO = Set.of("ui.irons_spellbooks.healing", "ui.irons_spellbooks.greater_healing",
            "ui.irons_spellbooks.aoe_healing");

    private IronsMagic() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(IronsMagic::onCast);
        NeoForge.EVENT_BUS.addListener(IronsMagic::onDamage);
        NeoForge.EVENT_BUS.addListener((LivingHealEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                MagicSchool.onHealed(event.getEntity(), event.getAmount());
            }
        });
    }

    private static void onCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        AbstractSpell spell = SpellRegistry.getSpell(event.getSpellId());
        var school = MagicSchool.byId(event.getSchoolType().getId().toString());
        if (school.isEmpty() || dealsDamage(spell, event.getSpellLevel(), player)) {
            return; // Damage spells train on their hits instead.
        }
        if (heals(spell, event.getSpellLevel(), player)) {
            school.get().creditHealing(player);
        } else {
            school.get().awardCast(player, event.getSpellId(), MagicSchool.CAST_XP + event.getSpellLevel() / 2);
        }
    }

    /** Whether a spell does damage, going by the info it lists (Iron's shows a damage line for every damage spell). */
    public static boolean dealsDamage(AbstractSpell spell, int level, ServerPlayer caster) {
        return lists(spell, level, caster, DAMAGE_INFO);
    }

    /** Whether a spell heals, going by the info it lists. */
    public static boolean heals(AbstractSpell spell, int level, ServerPlayer caster) {
        return lists(spell, level, caster, HEALING_INFO);
    }

    private static boolean lists(AbstractSpell spell, int level, ServerPlayer caster, Set<String> keys) {
        for (Component line : spell.getUniqueInfo(level, caster)) {
            if (line.getContents() instanceof TranslatableContents translatable && keys.contains(translatable.getKey())) {
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
                    ActivitySkills.awardAgainst(player, school.skill, ActivitySkills.perHit(event.getNewDamage()), event.getEntity()));
        }
    }
}
