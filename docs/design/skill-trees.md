# Skill trees: first pass (DD-70, DD-56, DD-81)

A draft for review: how every skill tree should look and play. Nothing here is built yet.

## Goals

- **Getting better at something.** Each tree is a short climb: a root bonus, a few choices, one signature ability, a
  capstone. Like the RPG Series' class trees, which go root bonus, then tiers of passives and spells, each spell with
  two upgrades.
- **Vertical and condensed.** One spine running down the screen, with side nodes one step left or right of it. About
  10–14 nodes a tree instead of today's flat rows.
- **Level gates.** Every node beyond the root needs a level in that skill as well as the node above it, so
  upgrades (Tree Feller II, III) can't be rushed.
- **Uncapped levels.** Skills level forever. Once a tree is full, leftover points go into Mastery (below), so a
  point is never wasted.
- **Big block-clearing is timed, not capped.** Tree Feller, Vein Miner and similar become mcMMO-style abilities:
  sneak and break to start them, then they run for a few seconds and go on cooldown.

## What Pufferfish's Skills can and can't do

- **Can:** gate a node by points earned in that skill (`required_points`). One point comes per level, so this is a
  level gate. It can also gate by points already spent in the tree (`required_spent_points`), and by how many of the
  nodes it connects to are taken.
- **Can't:** put several points into one node. Every node is bought once.
- **So ranks are stacked nodes.** "Tree Feller, up to 3 points" is three nodes stacked tightly in one column (I, II,
  III), each needing the one above and a higher level. It reads as one perk with three pips. A true multi-point node
  would mean our own skill screen in place of Pufferfish's, which is possible later but a big job.
- **Can:** levels with no limit (drop `level_limit`), and our code can read and spend a skill's unspent points through
  its API. That's what Mastery uses.

## Shared shape

Every tree uses the same five rows, top to bottom. Levels are the skill's own.

| Row | Level | What's there |
|---|---|---|
| Root | 0 | A small always-useful bonus. |
| Choices | 5 | Two side nodes (left and right); take either or both. |
| Signature | 10, then 20 and 35 | The tree's ability or key perk, with ranks II and III stacked under it. |
| Refinements | 25 | Two side nodes that change how the signature or the skill works. |
| Capstone | 50 | One node, also needing 12 points spent in the tree. |

Costs rise with depth: 1 for the root, 2 for the level 5 choices, 3, 5 and 8 for the signature ranks, 4 for the
refinements, 10 for the capstone (which also needs 30 points spent). Two outer columns hold small stat bonuses, five
ranks each at levels 2, 8, 15, 30 and 45, for 1 point each. A tree takes 49 points to fill, what a skill has at level
50; everything after that goes into Mastery.

## Mastery: the points sink

- Each skill has one Mastery stat. A point put into it raises that stat a little, with no limit on points, but with
  diminishing returns: the bonus approaches a cap (bonus = cap × (1 − 0.98^points)), so 35 points give half the cap.
- Points go in from our character screen (J): each skill row gets a "+ Mastery" button when it has unspent points.
  Our code takes the point back from Pufferfish and records the Mastery.
- Mastery stats are listed with each tree below.

## Active abilities (mcMMO-style)

- **Start:** sneak and break a matching block with the right tool. The ability starts with that block; a message and
  sound say so.
- **While active:** every matching block you break acts on the whole thing: a tree, an ore vein, a 3×3 of dirt. There's
  no block limit except a safety cap (256 per break) to keep the server smooth, and the tool still wears.
- **Duration and cooldown** come from the ability's rank, and Mastery lengthens the duration:

| Rank | Duration | Cooldown |
|---|---|---|
| I | 10 s | 4 min |
| II | 15 s | 3 min |
| III | 20 s | 2 min |

- **Feedback:** the ability shows on the HUD next to the skill XP popups: active time left, then cooldown left.

## Gathering

**Mining** (Mastery: +mining speed, cap +50%)
- Root: Quick Pick, +5% mining speed.
- L5: Prospector (10% chance of a second ore drop) | Stonemason (+15% speed on stone and deepslate).
- L10/20/35: **Vein Miner I–III** (active): mine a whole vein per block.
- L25: Sturdy Pick (15% chance a block costs no durability) | Deep Sight (ores within 6 blocks glow briefly when Vein
  Miner starts).
- L50 capstone: **Motherlode**: Prospector's chance doubles while Vein Miner is active.

**Woodcutting** (Mastery: +ability duration, cap +100%)
- Root: Sure Swing, +5% breaking speed on wood.
- L5: Forester (fell a tree's base: saplings replant) | Timber (+15% speed on logs).
- L10/20/35: **Tree Feller I–III** (active): fell a whole tree per log, natural trees only.
- L25: Sturdy Axe (15% no-durability chance) | Bark Collector (10% chance of a second log).
- L50 capstone: **Heartwood**: Tree Feller uses half the durability.

**Excavation** (Mastery: +treasure chance, cap +5%)
- Root: Digger, +5% speed on shovel blocks.
- L5: Treasure Sense (1% finds from dirt and sand: flint, clay, bones, rare gems) | Swift Shovel (+15% speed).
- L10/20/35: **Earthmover I–III** (active): each shovel block clears a 3×3 face.
- L25: Sturdy Shovel | Sifter (gravel always drops flint; sand sometimes drops glass shards).
- L50 capstone: **Buried Riches**: Treasure Sense chance triples while Earthmover is active.

**Farming** (Mastery: +extra-crop chance, cap +30%)
- Root: Green Thumb, +0.5 luck.
- L5: Replanter | Bountiful (15% extra crop).
- L10/20/35: **Harvest Moon I–III** (active): harvesting a ripe crop harvests (and replants, with Replanter) the ripe
  crops around it, 3×3 / 5×5 / 7×7.
- L25: Fertile Ground (crops near you grow faster) | Hardy (+2 max health).
- L50 capstone: **Golden Harvest**: rare golden carrots and apples from harvests.

**Fishing** (Mastery: +luck of the sea, cap +3)
- Root: Patient, +0.5 luck.
- L5: Quick Bite (fish bite faster) | Angler (+0.5 luck).
- L10/20/35: **Treasure Hunter I–III**: more and better treasure (enchanted books, dungeon ore).
- L25: Double Catch (10% chance of two fish) | Shake (pull a random drop off a mob with the rod).
- L50 capstone: **Master Angler**: rare dungeon keys and relic shards from fishing.

## Crafting

The level still sets the best ★ you can forge. Today's perks move onto the shared shape, with level gates.

**Forging** (Mastery: +sweet spot, cap +5%)
- Root: Apprentice Smith (no bonus; the tree's start).
- L5: Thrifty I (crack and salvage returns) | Slow Cooling I.
- L10/20/35: **Steady Hand I–III**: +2% sweet spot each.
- L25: Thrifty II | Slow Cooling II.
- L30/40: **Sure Strikes I–II** on the spine under them (25% and 50% less cracking).
- L50 capstone: **Masterwork**: a perfect forging (no misses) has a 10% chance to come out one ★ higher, up to what
  the forge allows.

**Leatherworking** (Mastery: +patch width, cap +5%)
- Root: Apprentice Tanner. L5: Water Saver I | Careful Tanner I. L10/20/35: Steady Scraper I–III.
- L25: Water Saver II | Careful Tanner II. L50 capstone: **Supple Hides**: 25% chance of a second refined leather.

**Weaving** (Mastery: +loom room, cap +5%)
- Root: Apprentice Weaver. L5: Careful Weaver I | Quick Fingers (a perfect first stitch finishes the cloth in two
  stitches instead of three). L10/20/35: Steady Needle I–III.
- L25: Careful Weaver II | Padding (padded cloth takes one less woven cloth). L50 capstone: **Fine Weave**: 25%
  chance of a second cloth.

## Combat styles

Each style: root +3% damage with it; L5 two passives; signature ability with ranks; L25 two refinements; capstone.
Mastery for all: +damage with that style, cap +20%.

| Style | L5 choices | Signature (L10/20/35) | L25 refinements | L50 capstone |
|---|---|---|---|---|
| One-Handed | Riposte (counter after a block) / Footwork (+speed) | **Bladestorm** (active): sweeping hits for the duration | Bleed / Disarm chance | Duelist: +25% damage vs a lone target |
| Two-Handed | Cleave (more sweep) / Crushing Blow (armor pierce) | **Skull Splitter** (active): every hit splashes | Stagger / Momentum | Executioner: +50% vs targets under 25% health |
| Polearms | Reach (+0.5 range) / Brace (knockback resist) | **Impale** (active): hits pierce through | Sweep / Keep Away | Phalanx: +armor while holding a polearm |
| Daggers | Backstab (+damage from behind) / Quick Hands (+speed) | **Shadow Flurry** (active): +attack speed | Poison edge / Vanish on kill | Assassinate: crit from stealth |
| Unarmed | Iron Fist / Deflect arrows | **Berserk** (active): bonus damage, break soft blocks by hand | Disarm / Iron Grip | Martial Master |
| Archery | Steady Aim (less spread) / Retrieval (arrows return) | **Volley** (active): extra arrows per shot | Daze / Piercing | Deadeye: headshot-style crits |
| Crossbows | Quick Reload / Bolt Pierce | **Rapid Fire** (active): instant reloads | Explosive bolts chance / Pin | Siegebreaker |
| Dual Wielding | Ambidextrous / Flurry | **Twin Fury** (active): off-hand hits doubled | Bleed / Parry | Whirlwind |
| Blocking | Braced / Bulwark | **Shield Wall** (active): blocks all damage from the front | Shield Bash / Reflect | Unbreakable |

## Armor

No actives; passives that reward wearing that weight. Mastery: +armor (heavy), +speed (light), +both (medium),
+spell power (mage), each with a cap.

| Armor | Root | L5 | Signature (L10/20/35) | L25 | Capstone |
|---|---|---|---|---|---|
| Light | +2% speed | Evasive (5% dodge) / Silent Step | **Agility I–III**: +dodge | Light Footed (no fall damage under 6) / Second Wind | Phantom: dodging grants speed |
| Medium | +1 armor | Balanced / Fitted Mail | **Adaptable I–III**: armor and speed | Steady (knockback resist) / Rally | Vanguard |
| Heavy | +1 toughness | Immovable / Iron Skin | **Juggernaut I–III**: damage reduction | Unshaken / Bulwark | Fortress: no movement penalty |
| Mage | +0.5 spell power | Woven Wards / Mana Weave | **Arcane Weave I–III**: spell power | Focus / Mana Shield | Archmage's Robe |

## Magic schools

Each school (Fire, Frost, Lightning, Arcane, Holy, Earth, Water, Air, Necromancy): root +spell power for the school;
L5 two choices (cooldown reduction, mana cost); signature **School Adept I–III** (+school spell power and a school
effect: burning, chill, chain, and so on); L25 two refinements; capstone a school-specific effect (Fire: Ignite spreads;
Frost: Shatter on frozen targets; Holy: overheal turns into a shield). Mastery: +school spell power, cap +25%.

## Beastmastery

Waits for companions (DD-61). Same shape when it comes.

## Questions for review

1. Is the five-row shape right for every tree, or should combat and magic trees be bigger than gathering ones?
2. Are stacked rank nodes (I, II, III in a tight column) close enough to "one perk with three points", given
   Pufferfish can't do more?
3. Mastery through a button on the J screen: OK, or would you rather it lived somewhere else?
4. The ability durations and cooldowns: a fair starting point?
5. Which trees to build first? Suggested: gathering and crafting (they're mostly designed), then combat, armor, magic.
