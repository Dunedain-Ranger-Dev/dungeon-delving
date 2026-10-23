package io.github.ranger3462.dungeondelving.skill;

import java.util.Map;
import java.util.Optional;

/**
 * The magic schools (DD-81), each its own skill tree that levels from casting its spells, so a player can master frost
 * and still be a fire apprentice. Spell Engine (the RPG Series) and Iron's Spells both feed them, by their own school
 * ids.
 */
public enum MagicSchool {
    FIRE("fire_magic"),
    FROST("frost_magic"),
    LIGHTNING("lightning_magic"),
    ARCANE("arcane_magic"),
    HOLY("holy_magic"),
    EARTH("earth_magic"),
    WATER("water_magic"),
    AIR("air_magic"),
    /** Soul and eldritch magic, and blood magic too. */
    NECROMANCY("necromancy");

    /** Experience for each spell cast: a base, plus some for its level (Iron's) or for a full cast (Spell Engine). */
    public static final int CAST_XP = 4;

    private static final Map<String, MagicSchool> SCHOOLS = Map.ofEntries(
            Map.entry("spell_power:fire", FIRE), Map.entry("irons_spellbooks:fire", FIRE),
            Map.entry("spell_power:frost", FROST), Map.entry("irons_spellbooks:ice", FROST),
            Map.entry("spell_power:lightning", LIGHTNING), Map.entry("irons_spellbooks:lightning", LIGHTNING),
            Map.entry("spell_power:arcane", ARCANE), Map.entry("irons_spellbooks:ender", ARCANE), Map.entry("irons_spellbooks:evocation", ARCANE),
            Map.entry("spell_power:healing", HOLY), Map.entry("spell_power:holy", HOLY), Map.entry("irons_spellbooks:holy", HOLY),
            Map.entry("spell_power:earth", EARTH), Map.entry("spell_power:nature", EARTH), Map.entry("irons_spellbooks:nature", EARTH),
            Map.entry("spell_power:water", WATER),
            Map.entry("spell_power:air", AIR),
            Map.entry("spell_power:blood", NECROMANCY), Map.entry("irons_spellbooks:blood", NECROMANCY),
            Map.entry("spell_power:soul", NECROMANCY), Map.entry("spell_power:unholy", NECROMANCY), Map.entry("irons_spellbooks:eldritch", NECROMANCY));

    /** A non-damage, non-healing spell (a buff, a shield, a teleport) gives cast XP at most this often, per spell. */
    public static final int CAST_XP_COOLDOWN_TICKS = 600;
    private static final java.util.Map<java.util.UUID, java.util.Map<String, Long>> LAST_CAST_XP = new java.util.HashMap<>();
    /** Healing a player was just credited with casting: their school, until this game time. Heals land a moment after the cast. */
    private record HealCredit(MagicSchool school, long until) {}
    private static final java.util.Map<java.util.UUID, HealCredit> HEAL_CREDITS = new java.util.HashMap<>();
    /** How long after casting a healing spell its heals count, in ticks, and how far from the caster. */
    public static final int HEAL_CREDIT_TICKS = 40;
    public static final double HEAL_CREDIT_RANGE = 16.0;

    public final String skill;

    MagicSchool(String skill) {
        this.skill = skill;
    }

    /**
     * Gives cast XP for a buff or utility spell, unless this player got XP for the same spell within
     * {@link #CAST_XP_COOLDOWN_TICKS}: such spells have nothing to measure, so this keeps spamming them from paying.
     */
    public void awardCast(net.minecraft.server.level.ServerPlayer player, String spellId, int xp) {
        long now = player.level().getGameTime();
        var last = LAST_CAST_XP.computeIfAbsent(player.getUUID(), id -> new java.util.HashMap<>());
        Long previous = last.get(spellId);
        if (previous != null && now - previous < CAST_XP_COOLDOWN_TICKS) {
            return;
        }
        last.put(spellId, now);
        ActivitySkills.award(player, skill, xp);
    }

    /** A player just cast a healing spell of this school: heals near them in the next moment train it. */
    public void creditHealing(net.minecraft.server.level.ServerPlayer caster) {
        HEAL_CREDITS.put(caster.getUUID(), new HealCredit(this, caster.level().getGameTime() + HEAL_CREDIT_TICKS));
    }

    /**
     * Something was healed: the nearest player who just cast a healing spell (themselves included) gets XP for the
     * health actually restored, so healing someone already at full health trains nothing.
     */
    public static void onHealed(net.minecraft.world.entity.LivingEntity healed, float amount) {
        float restored = Math.min(amount, healed.getMaxHealth() - healed.getHealth());
        if (restored <= 0 || HEAL_CREDITS.isEmpty() || !(healed.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        HEAL_CREDITS.values().removeIf(credit -> credit.until() < now);
        net.minecraft.server.level.ServerPlayer best = null;
        for (var player : level.players()) {
            if (HEAL_CREDITS.containsKey(player.getUUID()) && player.distanceToSqr(healed) <= HEAL_CREDIT_RANGE * HEAL_CREDIT_RANGE
                    && (best == null || player.distanceToSqr(healed) < best.distanceToSqr(healed))) {
                best = player;
            }
        }
        if (best != null) {
            ActivitySkills.award(best, HEAL_CREDITS.get(best.getUUID()).school().skill, ActivitySkills.perHit(restored));
        }
    }

    /** The school a Spell Engine or Iron's Spells school id belongs to. Empty for martial "schools" (melee, ranged) and the Witcher's signs. */
    public static Optional<MagicSchool> byId(String id) {
        return Optional.ofNullable(SCHOOLS.get(id));
    }
}
