# Player systems (Phase 2)

The player side of the pack: the RPG Series, forging that stays relevant alongside it, ability scores, races, classes,
and mcMMO-style skill trees. Goals set by the user on 2026-10-23; this is the working plan, not a finished design.
Open questions are marked **Q**.

## What's already there

- **RPG Series** (DD-76): Better Combat, Spell Engine, Wizards, Paladins & Priests, Rogues & Warriors, Archers,
  Jewelry, Relics, Arsenal, Armory, Skill Tree, plus the More RPG Classes add-ons (Berserker, Forcemaster, Witcher,
  Elemental Wizards, Archers Expansion). They load cleanly next to Iron's Spells, Ars Nouveau and Apotheosis, and
  all game tests pass with them. Only upstream warnings so far.
- **Pufferfish's Skills** (pulled in by Skill Tree): a data-driven skill-tree engine. Each *category* is one tree
  with its own experience, levels and points. Experience comes from built-in sources (mine block, break block, kill
  entity, deal or take damage, craft item, smelt item, enchant item, fish item, eat food, heal, criterion, increase
  stat), with anti-farming limits per chunk or per entity. Nodes give built-in rewards: attributes, commands,
  scoreboard values, tags, points. Our own code can react to tags (or a custom reward type) for things like vein
  mining. Skill Tree (RPG Series) already ships its class trees on it.
- **Dungeon Difficulty**: no rules for the dungeon dimension, but its per-player scaling applies everywhere. Each
  extra player in multiplayer gives all mobs +20% health and +10% damage, on top of our tier scaling. Revisit when
  we tune multiplayer.

## 1. Integrating the RPG Series

The dev build is done. What's left:
- Add them to the pack (MODS.md, export) once the forging rework below is in, so the first pack with them already
  has the right recipes.
- Keybinds are left for release polish. Known clashes: R (Combat Roll and Iron's spell wheel), Left Alt (Iron's spell
  bar and Spell Engine's skip-hotbar key), K (Skills menu, Apotheosis and KubeDex).
- Weapon attributes: our own weapons (Goblin Spear, Master Anvil gear) need Better Combat attribute files so they
  swing properly.

## 2. Keeping forging relevant

The RPG mods add about 1,300 recipes, but most are smelting gear back down. The ones that matter are about 250
shaped crafting recipes (base class gear, jewelry, relics) and 380 smithing upgrades (diamond to netherite, Armory
and Arsenal tiers). Plan: a rule-based KubeJS script in the pack (where recipe overrides already live), not
hand-editing 600 recipes.
- Shaped class gear: swap plain ingots and gems for our forged parts and tempered ingots of a matching tier.
- Smithing upgrades: need a dungeon ingot of the target tier (e.g. Emberstone for diamond-class gear, Thailite for
  netherite) and a Master Anvil step, so the best class gear comes from dungeons plus forging.
- Jewelry: gem settings use our ingots.
- **Q:** should class gear go through the Master Anvil's forging minigame (quality and stars like our own gear), or
  just take forged parts as ingredients in normal crafting? The first keeps forging central but is more work per item.

## 3. Ability scores

STR, DEX, CON, INT, WIS, CHA, each a real attribute so gear, races and skills can modify them the usual way.
- Each point feeds existing attributes, so the RPG mods, Better Combat and Iron's all feel it:
  - STR: melee damage, knockback resistance.
  - DEX: attack speed, ranged damage (Ranged Weapon API), crit chance (Critical Strike).
  - CON: max health, armor toughness.
  - INT: arcane, fire and frost spell power (Spell Power) and Iron's spell power.
  - WIS: healing and holy spell power, mana regen.
  - CHA: trading prices now; faction standing and companions later.
- Points come from levelling up, and from rare items (tomes, shrine offerings, boss drops).
- A stats screen to spend them, plus a respec item.
- **Q:** which level? Vanilla XP levels (simple, but enchanting and Apotheosis eat them) or a separate character
  level earned from fighting and dungeons (recommended).

## 4. Races

Chosen once, on first join, from a screen. D&D-flavoured: each gives ability score bonuses and one or two small,
always-on traits. Built in our mod, not Origins.

| Race | Scores | Traits (first pass) |
|---|---|---|
| Human | +1 to all | Extra skill point every few levels |
| Dwarf | +2 CON, +1 STR | Night vision underground, faster mining of dungeon ores |
| Elf | +2 DEX, +1 INT | No fall damage from short drops, keener bow accuracy |
| Halfling | +2 DEX, +1 CHA | Smaller hitbox, luck on loot |
| Half-Orc | +2 STR, +1 CON | Once per fight, drops to 1 health instead of dying |
| Gnome | +2 INT, +1 WIS | Cheaper spell costs, better crafting yield |
| Tiefling | +2 CHA, +1 INT | Fire resistance |
| Dragonborn | +2 STR, +1 CHA | A short-cooldown breath attack |

**Q:** this race list, and whether races also tie into factions later (e.g. dwarves start friendly with a dwarven
faction).

## 5. Classes

Four base classes, picked after the race, each opening one RPG Series line:
- **Fighter**: Rogues & Warriors (warrior side).
- **Archer**: Archers.
- **Rogue**: Rogues & Warriors (rogue side).
- **Mage**: Wizards.

Specializations unlock deeper in the skill tree, using the other mods' content:
- Fighter: Paladin (Paladins & Priests), Berserker, Forcemaster.
- Archer: Ranger (Archers Expansion), Witcher.
- Rogue: Assassin, Witcher.
- Mage: Elemental Wizard, Priest (Paladins & Priests), Necromancer (our own, drawing on Iron's blood and evocation
  schools, or Spell Engine spells we write).

Classes decide which Skill Tree categories a player can level, and which class gear they get full bonuses from.
**Q:** can a player change class (at a cost), or is it permanent per character?

## 6. Skill trees (mcMMO-style)

Profession trees on Pufferfish's Skills, alongside the class trees. Doing the activity earns that tree's
experience, a level gives a point, and points buy nodes.
- **Mining** (mine block): mining speed, double drops, then vein mining.
- **Woodcutting** (break log): speed, double logs, then tree felling.
- **Excavation**, **Farming**, **Fishing**: the same pattern.
- **Smithing**, **Leatherworking**, **Weaving** (our crafting): experience from forging and crafting, and nodes that
  raise success rate, quality (stars) and yield. These feed straight into the forging system.
- Vein mining, tree felling and the forging bonuses are our code, switched on by a node (a tag or a custom reward
  type).

## 7. Better Combat for our mobs

**Better Mob Combat: Reimagined** (MIT, NeoForge 1.21.1) gives humanoid mobs Better Combat's weapon swings. It lists
vanilla mobs; whether it picks up our ArmedHumanoid mobs automatically is untested. Plan: a spike that adds it, gives
the Goblin Spear and our other weapons Better Combat attributes (spear thrusts, dual-wield sword and axe), and checks
in game. If the add-on won't take our mobs, we drive Better Combat's animations from our own attack goal instead.

## Suggested order

1. Better Combat mob spike (quick, answers a feasibility question).
2. Forging rework of the RPG recipes, then add the RPG Series to the pack.
3. Ability scores (races and classes build on them).
4. Races, then classes.
5. Profession skill trees, starting with Mining and Smithing.
