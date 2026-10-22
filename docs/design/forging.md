# Forging, leatherworking and weaving

Jira: DD-62 (sub-tasks DD-63 to DD-69; talent tree DD-70).

Hands-on crafting for custom armor and weapons, modeled on the World of Maria "Forge 2.0" plugin, but less grindy. The
two main differences are timing instead of pure luck, and scrap instead of lost metal. Numbers here are starting
points, and balancing waits for playtesters.

## The forge

A **Forge Hearth** block is the center. Within 7 blocks across and 4 up or down, it looks for these stations, which
can be arranged any way:

| Station | Basic | Upgrade (raises the forge level) |
|---|---|---|
| Heat | Furnace | Blast Furnace (twice as fast), plus **Bellows** next to it (each pump pushes the heating along) |
| Strike | Anvil (any damage state) | **Dwarven Anvil**: wider sweet spot |
| Quench | Water cauldron | **Quench Tub** filled with Quenching Oil: no crack chance |
| Grind | Grindstone | **Grinding Wheel**: wider sweet spot |

**Forge level** caps the highest ★ you can make:

| Level | Needs | Highest ★ |
|---|---|---|
| 1 | The basic stations | ★3 |
| 2 | Blast Furnace and Dwarven Anvil | ★5 |
| 3 | Level 2, plus Bellows, Quench Tub and Grinding Wheel | ★7 |

**Workshop bonus:** decoration blocks near the hearth (the `dungeondelving:forge_decor` tag: bricks, stone and
deepslate bricks, chains, lanterns, iron bars, barrels, smithing tables, campfires and so on) widen every sweet spot.
Each 8 blocks add +0.5%, up to +2.5%.

Right-click the hearth to see what's missing, the forge level and the workshop bonus. The hearth's fire lights up once
the forge is complete. Striking, quenching and grinding only work at stations that are in range of a complete hearth.

## The loop

1. **Heat.** Smelt an iron ingot (furnace or blast furnace) to get a **Heated Ingot**. The ingot starts cooling the
   moment you take it out, and stays workable for about 30 seconds (the Forging skill slows this down). A cold heated
   ingot can go back in the furnace with no loss. To go for a higher ★, smelt a Tempered Ingot ★n instead; it comes out
   as a heated ingot aiming for ★n+1. Bellows next to the furnace: right-click to pump, and each pump pushes the
   heating along.
2. **Strike.** Hold the hot ingot and right-click the anvil. A bar appears with a marker sweeping back and forth across
   it. Each fresh right-click is one strike; holding the button down doesn't repeat it. You need 3 strikes, plus 1 more
   for every 2 ★ you're aiming for. The result is a **Struck Ingot**, which is still hot.
   - **The bar:** the green sweet spot is 9% of the bar (13% on a Dwarven Anvil), narrowing a little as the target ★
     rises. Its middle quarter is **gold**. The marker restarts from the left for each strike.
   - **Cooling metal sweeps faster:** up to 40% faster as the metal goes from glowing to cold. Reheating resets it.
   - **Gold:** the strike counts twice and never cracks.
   - **Green:** safe on glowing-hot metal.
   - **Miss:** may crack the piece. The first miss in a bar has a 30% crack chance, and each further miss adds 15%.
   - **Basic anvil:** a vanilla anvil spoils the piece 10% of the time when a bar is finished, however well it went.
     The Dwarven Anvil never does.
   - **Lag:** the client times each bar itself. The marker starts sweeping when the bar arrives, and a click sends how
     long the bar had been sweeping, so lag never shifts it: what the player sees is what's judged. Each strike is
     tied to its bar, and the bar freezes after a click until the next one arrives, so a late packet can't land on the
     wrong bar. The server only pulls back a timing that the player's ping can't explain (more than 10 ticks out).
     A hacked client could cheat this; that's accepted for a co-op pack.
3. **Quench.** Right-click a water cauldron while the struck ingot is still hot. Water has a flat 30% crack chance that
   skill doesn't lower; only the Quench Tub's oil avoids it. Each quench uses a level of water (or of oil), so a full
   cauldron does 3 quenches before it needs refilling. An empty cauldron still counts as the forge's quench station.
   You get a **Quenched Ingot**.
4. **Grind.** Right-click the grindstone and use the same timing bar, with 2 passes. The green is 10% of the bar (14%
   on the Grinding Wheel), and gold works the same as on the anvil. Metal can be ground cold.
   - Every pass clean: you get a **Tempered Ingot ★n**.
   - Any pass missed: you get an **Unstable Ingot** instead. Grind it again: clean, and it's tempered; another miss,
     and it breaks.
   - A vanilla grindstone spoils a clean grind 10% of the time; the Grinding Wheel never does.

**Cracking** turns the piece into 5 iron nuggets. That rises with Forging level, and talents raise it further later.

## Flux and parts

Flux is based on real smithing flux: blacksmiths used silica sand, then borax.

| Flux | Recipe | Used for parts up to |
|---|---|---|
| Sand Flux | sand + bone meal | ★3 |
| Borax Flux | Borax (a dungeon mineral, T2+ resource markers) + sand | ★5 |
| Runic Flux | Borax Flux + Runic Shard III | ★6 |
| Runeforged Flux | Borax Flux + Runic Shard IV | ★7 |

Parts are crafted from tempered ingots plus flux. Each part takes the **lowest ★** among its ingots.

| Part | Recipe |
|---|---|
| Armor Plate | 2 tempered ingots + flux → 1 |
| Rivets | 1 tempered ingot + flux → 4 |
| Chain Links | 1 tempered ingot + flux → 4 |

## Leather and weaving

**Leather:**
- Refined Leather: leather + honeycomb.
- Boiled Leather: right-click a water cauldron over a campfire or fire while holding leather.
- Hardened Leather: 3 boiled leather + string + honeycomb → 3.

**Weaving:**
- Woven Cloth: hold string and right-click a loom. The same timing bar plays, with 2 passes. A miss wastes nothing, but
  a perfect weave gives a second cloth.
- Padded Cloth: 2 woven cloth + wool.

**Bases** go under every armor piece, one per slot:
- Leather Base: refined leather, for medium and heavy armor.
- Gambeson Base: padded cloth, for light armor.

## Forged gear

Each piece takes the lowest ★ among its parts. Stats are set on the piece when it's crafted (armor, toughness,
knockback resistance and durability), so one item per slot covers every ★.

| Weight | Recipe per piece | Armor | Toughness | Other |
|---|---|---|---|---|
| Light (Brigandine) | gambeson base + hardened leather + rivets | ×0.6 | none | no penalty |
| Medium (Hauberk) | leather base + chain links + rivets | ×0.8 | ×0.5 | none |
| Heavy (Plate) | leather base + armor plates + rivets + hardened leather | ×1.0 | ×1.0 | knockback resistance, −1.25% speed per piece |

Heavy set totals for comparison: iron is 15 armor; diamond is 20 armor and 8 toughness; netherite is 20 armor, 12
toughness and 0.4 knockback resistance.

| ★ | Armor | Toughness | Knockback resistance | Durability multiplier | Perk |
|---|---|---|---|---|---|
| ★1 | 17 | 1 | 0 | 19 | none |
| ★2 | 19 | 4 | 0 | 23 | none |
| ★3 | 20 | 9 | 0 | 27 | none |
| ★4 | 20 | 12 | 0.2 | 31 | +1 Apotheosis socket |
| ★5 | 21 | 13 | 0.3 | 35 | +2 sockets |
| ★6 | 22 | 14 | 0.3 | 39 | +2 sockets, attribute affixes ×1.15 |
| ★7 | 23 | 15 | 0.4 | 43 | +2 sockets, affixes ×1.3, crafted with a rolled Apotheosis rarity |

**Weapons:** Forged Sword (plate + plate + grip), Forged Axe and Forged Shield. Sword damage goes from 6.5 at ★1 to 8.6
at ★7, against iron 6, diamond 7 and netherite 8.

**Apotheosis** is optional, and all of this is skipped without it. The ★ is stored separately from Apotheosis's data,
so reforging keeps it. Sockets come from `GetItemSocketsEvent`. The multiplier scales the attribute modifiers that
affixes add; other affix effects are untouched.

**Repair:** in the vanilla anvil, with a tempered ingot of the same ★ or higher.

**Salvage:** sneak and right-click the grindstone with forged gear. You get back some of its parts, and how many
depends on Forging level.

## Skills

Forging, Leatherworking and Weaving each have levels 1 to 30, from XP. There are no talents yet (DD-70); perks unlock
at set levels instead.

**Forging XP:**

| Action | XP |
|---|---|
| Perfect strike | 3 |
| Off strike | 1 |
| Quench | 2 |
| Grind | 3 |
| Tempered ingot | 10 × ★ |
| Gear piece crafted | 15 × ★ |

**Forging perks:**
- Every level: +0.25% sweet-spot width.
- L5: ingots cool 25% slower.
- L10: cracks return 6 nuggets.
- L15: strikes are 25% less likely to crack.
- L20: cracks return 7 nuggets, and ingots cool 50% slower.
- L25: cracks return 8 nuggets.
- L30: strikes are 50% less likely to crack.

**Forging level needed for each ★:**

| ★1 | ★2 | ★3 | ★4 | ★5 | ★6 | ★7 |
|---|---|---|---|---|---|---|
| 1 | 3 | 6 | 10 | 15 | 20 | 25 |

**Leatherworking** gives XP for boiling and hardening leather and for crafting bases, with perks for extra yield.
**Weaving** gives XP for weaving, with perks for a wider loom sweet spot and extra yield.

`/dungeondelving skills` shows your levels. `/dungeondelving skills set <skill> <level>` is for testing.

## Later

- The talent tree (DD-70).
- A JEI page and a Patchouli guide for the process.
- More metals: gold, then dungeon ores.
- Weapon types beyond the sword and axe.
