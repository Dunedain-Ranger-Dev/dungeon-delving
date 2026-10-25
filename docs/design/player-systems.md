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

**Where it landed (DD-89, 2026-10-25), replacing the plans below:** one set of forged gear, Dungeon Delving's own
(forged sword and axe; brigandine, hauberk and plate armor), each made like its iron counterpart but from forged parts,
its ★ from those parts. RPG and vanilla gear keep their own recipes and stay unstarred. Later: forged gear for more
weapon and armor classes, and variant recipes for specific looks. Until forged gear has its own art, a cosmetic mod can
let players wear the look of other gear.

The RPG mods add about 1,300 recipes, but most are smelting gear back down. The ones that matter are about 250
shaped crafting recipes (base class gear, jewelry, relics) and 380 smithing upgrades (diamond to netherite, Armory
and Arsenal tiers). Plan: a rule-based KubeJS script in the pack (where recipe overrides already live), not
hand-editing 600 recipes.
- Shaped class gear: swap plain ingots and gems for our forged parts and tempered ingots of a matching tier.
- Smithing upgrades: need a dungeon ingot of the target tier (e.g. Emberstone for diamond-class gear, Thailite for
  netherite) and a Master Anvil step, so the best class gear comes from dungeons plus forging.
- Jewelry: gem settings use our ingots.
- **Leaning (user, 2026-10-23):** full integration, so RPG gear can earn stars from quality too, if the cost is
  reasonable. The likely way: one generic quality component our Master Anvil can put on *any* item, scaling that
  item's own attribute modifiers by its stars (an item attribute event), rather than per-mod special cases. The
  forging minigame would then accept RPG gear recipes as "forge this item" jobs. To be costed before committing.

### Costing full integration (2026-10-23)

What's there now: stars are a `STARS` component, but only our own gear classes (`ForgedGear.Armor`, `Sword`, `Axe`)
turn stars into stats, by overriding their attribute modifiers per stack. Starred crafting recipes
(`starred_shaped`/`starred_shapeless`) already give a result the lowest ★ of its forged inputs, capped by the flux.
The RPG gear: about 230 shaped recipes (vanilla iron, gold, diamond, leather, chain, wool, string; Witcher's own
metals and diagrams), about 190 smithing upgrades (mostly netherite, plus Armory upgrade items and warden crystals).
Many of the rest only load with Better End, Better Nether or Jewelry, which the pack doesn't have.

Two ways:
- **A. Parts only (no stars on RPG gear).** A KubeJS rule table swaps vanilla inputs for ours: iron for tempered
  ingots or armor plates, chain for chain links, leather for refined or hardened leather, wool for padded cloth.
  RPG gear keeps its own stats. About a day. Forging is a gate, not a quality source.
- **B. Full integration (stars on any gear).** On top of A's rule table:
  1. Quality core (mod): an `ItemAttributeModifierEvent` hook scales a starred item's own armor, toughness, attack
     damage, ranged damage and spell power modifiers by a ★ multiplier (speed and the like untouched); durability is
     set from the ★ when crafted; stars show in the tooltip (we can't rename other mods' items the way we do ours).
     Apotheosis and other mods see the scaled values like any item's. 1–2 days with tests.
  2. Recipes: the KubeJS rules emit our `starred_shaped` type instead of vanilla's, so the ★ flows from the forged
     parts with no new minigame step. Smithing upgrades copy the base item's components, so a ★5 iron set stays ★5
     when upgraded; the upgrade just needs our dungeon ingot. 1–2 days, mostly reviewing the rule table's output.
  3. Balance and an in-game pass: about a day.
  About 4–5 days in total, against A's one.

Complications for B, and what I'd do:
- **Gear with no metal** (robes from wool, bows from string and sticks, wooden and stone weapons) has no star source.
  Robes could take stars from a starred thread or rune part later; until then they stay unstarred. Wooden and stone
  tiers can stay vanilla.
- **Witcher's metals** (steel, silver, dimeritium, meteorite and their dark variants) are a second metal ladder.
  Simplest: steel becomes our coke-infused iron route (already started), and the rarer ones map to dungeon ingots.
- **Unstarred RPG gear** from loot or other recipes keeps its base stats, so the multiplier should put ★3 at about
  base (×1.0), with lower stars a little under and ★7 about +25%, matching our sword's spread (6.5 to 8.6).
- **Stacking with RPG upgrades** is fine: smithing keeps the component, so stars and the Armory tiers combine.

Recommendation: B, built in that order, so step 1 can be tested on a few items before the recipe sweep.

**Chosen (user, 2026-10-23): B.** The quality core is in (`forging/Quality.java`). The recipe sweep is the pack's
`tools/rpg_forging.py`, which writes overrides into `kubejs/data/<mod>/recipe/`:
- Iron gear takes armor plates, chain links and rivets, and so its stars; leather, wool and hardened leather become
  ours.
- Diamond gear is an upgrade of the iron piece with Emberstone (no template), and netherite an upgrade of the diamond
  piece with Thailite. The gold tier is off; wood, stone and flint stay.
- Witcher: steel and silver armor forged like iron (silver trim is Thraggite), the other swords are upgrades of the
  steel sword, diagram tiers take tier 1–4 ingots, and its metals elsewhere map to dungeon ingots.
- Bows, staves, wands, robes and leather gear stay unstarred until DD-86 (woodworking, leather and cloth quality).
- Jewelry is untouched for now.

## 3. Ability scores

STR, DEX, CON, INT, WIS, CHA, each a real attribute so gear, races and skills can modify them the usual way.
- Each point feeds existing attributes, so the RPG mods, Better Combat and Iron's all feel it:
  - STR: melee damage, knockback resistance.
  - DEX: attack speed, ranged damage (Ranged Weapon API), a little movement speed. Crit chance comes from gear,
    not scores (user, 2026-10-23).
  - CON: max health, armor toughness.
  - INT: arcane, fire and frost spell power (Spell Power) and Iron's spell power.
  - WIS: healing and holy spell power, mana regen.
  - CHA: trading prices now; faction standing and companions later.
- Points come from levelling up, and from rare items (tomes, shrine offerings, boss drops).
- A stats screen to spend them, plus a respec item.
- **Decided (user, 2026-10-23):** a separate character level, worked out from the skill levels like RuneScape's,
  so crafting and gathering count as much as fighting and nobody is punished for crafting. Exact formula to be
  worked out (e.g. an average of the highest skills, or total level in steps).

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

**Decided (user, 2026-10-23):** four base classes to start: **Fighter**, **Archer**, **Rogue** and **Mage**. Each
gets its own distinct skill tree (replacing the RPG Series' single radial class tree), locked until the class is
picked; every other tree is open to everyone from the start. Specializations (Paladin, Necromancer, Berserker,
Witcher and so on) come later as branches of the class trees, drawing on the RPG Series content. Players can change
class at a cost (a rare item or ritual that resets the class and its points).

Magic isn't locked to the Mage: anyone can learn any school (a Rogue can still cast Fireball). The Mage class
empowers casting instead: mana regen, spell damage and the like.

### Getting a class: the new-player flow

1. Join the server and pick a **race** (section 4).
2. Pick a **faction capital** to start in. Capitals are protected safe zones that other factions can't attack
   outside of specific events.
3. An NPC greets you and gives you a basic **adventuring book** that points you to the local **guild**.
4. The guild walks you through combat, forging and our other systems (tutorial quests; FTB Quests is already in the
   pack).
5. At the end, you're sent to a **class tutor** to pick a class, which unlocks its tree.

Steps 2 to 5 need things that don't exist yet (faction capitals and safe zones, NPCs, the guild tutorial), so they
come after the player systems themselves; races and class picking can work from a screen until then.

## 6. Skill trees (mcMMO-style)

**Decided (user, 2026-10-23):** every skill levels on its own, from doing that thing. Doing it earns the tree's
experience, a level gives a point, and points buy nodes. All trees run on Pufferfish's Skills. The RPG Series' own
radial class tree and weapon tree are retired in favour of these (switched off by the pack's data).

**Professions** (DD-80, built):
- **Mining**, **Woodcutting**, **Excavation**, **Farming**, **Fishing**: from mining, chopping, digging,
  harvesting and fishing. Later nodes: double drops, vein mining, tree felling.
- **Forging**, **Leatherworking**, **Weaving**: from our crafting; nodes for success rate, quality and yield.

**Combat styles** (from hitting with that kind of weapon, by its Better Combat type):
- **One-Handed**: sword, rapier, cutlass, katana, axe, mace.
- **Two-Handed**: claymore, hammer, heavy axe, double axe.
- **Polearms**: spear, halberd, glaive, lance, scythe, trident.
- **Daggers**: dagger, sickle, claw, soul knife.
- **Unarmed**: fists and knuckles (Forcemaster).
- **Archery**: bows. **Crossbows**: crossbows.
- **Dual Wielding**: hitting with a weapon in each hand.
- **Blocking**: blocking hits with a shield.

**Armor** (from taking hits while wearing mostly that kind of armor):
- **Light**, **Medium**, **Heavy** and **Mage** (robes). Armor is sorted by item tags, with our forged armor's weight
  and sensible defaults for vanilla and the RPG Series.

**Magic schools** (from casting that school's spells, in Spell Engine or Iron's Spells), each levelling on its own so
you can master frost and still be a fire apprentice:
- **Fire** (fire), **Frost** (frost, Iron's ice), **Lightning**, **Arcane** (arcane, Iron's ender and evocation),
  **Holy** (healing, Iron's holy), **Earth** (earth, Iron's nature), **Water**, **Air**, **Necromancy** (soul, Iron's
  eldritch and blood; blood was folded in to keep the tree count down).
- The Witcher's signs stay with a future Witcher specialization, not the shared schools.

**Beastmastery** (planned; no pets system yet): from taming and breeding animals for now, and from fighting alongside
companions once those exist.

**Classes**: one tree each for Fighter, Archer, Rogue and Mage (section 5), locked until picked.

## 7. Better Combat for our mobs

**Better Mob Combat: Reimagined** (MIT, NeoForge 1.21.1) gives humanoid mobs Better Combat's weapon swings. It lists
vanilla mobs; whether it picks up our ArmedHumanoid mobs automatically is untested. Plan: a spike that adds it, gives
the Goblin Spear and our other weapons Better Combat attributes (spear thrusts, dual-wield sword and axe), and checks
in game. If the add-on won't take our mobs, we drive Better Combat's animations from our own attack goal instead.

## Suggested order

Done: Better Combat mob swings (DD-79), the character core (DD-80: professions, character level, ability scores).

1. Combat style, armor and magic school trees, plus Beastmastery's first XP (the rest of the skill breakdown).
2. Races, with a first-join race screen.
3. Classes: the four class trees and a class pick, retiring the RPG Series' radial tree.
4. Forging rework of the RPG recipes, then add the RPG Series to the pack.
5. The new-player flow (capitals, greeter NPC, guild tutorial, class tutor), once factions and capitals exist.
