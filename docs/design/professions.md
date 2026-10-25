# Crafting professions

The plan for the crafting professions: a shared workshop, Forging as it is, Leatherworking and Weaving rebuilt,
Woodworking new. Agreed direction 2026-10-24; details here are a first pass, and numbers wait for playtesting. Nothing
is built yet.

**Decided:** the shared block is the **Guild Ledger**. New stations reuse vanilla models where they can, but new ones
are fine. **Farmer's Delight** joins the pack for its bark, straw, canvas, knives and cooking pot. Dryad dungeons are
**mixed**: groves to explore, with puzzles, trades and fights.

## Goals

- **Full gear from crafting.** Armor, melee weapons, bows, crossbows, staves, wands and shields can all be made, with
  quality (★) from how well the steps went.
- **Like the real craft.** Each profession follows its real process in a few steps.
- **Something that takes time.** Each profession has one step that cooks, soaks or dries while you do something else.
- **Many sources.** Each pulls materials from the other skills.
- **Dungeons for the best.** The top tiers need dungeon-only materials, each profession tied to a faction.
- **Vanilla first.** Vanilla blocks and tools wherever they fit.

## Farmer's Delight: what it brings

| From Farmer's Delight | Used for |
|---|---|
| Tree Bark (stripping logs with an axe) | Tanning liquor |
| Straw (harvesting with a knife) → Canvas | The plant fibre for Weaving's coarse tier |
| Rope | Bindings, and the Shaping Rack's recipe |
| Knives (flint to netherite) | The cutting tool at the Shaping Rack, beside shears |
| Cooking Pot (a timed, heated pot) | Brewing the tanning liquor |
| Rice, cabbage, tomatoes, onions | Cooking, and more Farming XP sources |

To confirm when adding: exact item ids and that each works this way on 1.21.1.

## The Guild Ledger (replaces the Forge Hearth)

- **What it is:** a lectern-like block (a lectern model with a ledger on it), crafted from a lectern and a book and
  quill. It marks a guild workshop for every profession.
- **Range:** 7 blocks across and 4 up or down, as now. One ledger can cover a forge, tannery, weaving room and woodshop.
- **Right-click:** lists each profession's stations in range, what's missing, and each profession's workshop level.
- **Workshop level, per profession:** 1 with the basic stations, 2 and 3 with upgrades. It caps the best ★ there:
  ★3, ★5, ★7.
- **Decor bonus, per profession:** each has a decor tag that widens its sweet spots, +0.5% per 8 blocks up to +2.5%:

| Profession | Decor |
|---|---|
| Forging | Bricks, chains, lanterns, iron bars, anvils, smithing tables |
| Leatherworking | Barrels, hay bales, logs, item frames, leather-colored wool |
| Weaving | Wool, carpets, banners, looms, flower pots |
| Woodworking | Logs, planks, scaffolding, stripped logs, bookshelves |

- **Hearths:** the Forge Hearth goes; any already placed turn into Guild Ledgers.
- **DD-84 tie-in:** a capital's guild hall starts with a ledger, so new players have a workshop to learn in.

## Quality, the same rules everywhere

- **★ steps:** one or two minigames per profession set the ★. Each has its own kind of input (below), never a reskin.
- **★ cap from a material,** like forging's flux: the tanning liquor, the thread, the wood finish.
- **★ cap from the workshop level and the skill level,** as Forging does now.
- **The timed step never sets the ★.** It's only a wait.
- **A piece's ★** is the lowest ★ among its main parts.

### The minigames, by feel

| Minigame | Profession | Input |
|---|---|---|
| Strike, Grind | Forging | Tap when the sweeping marker is in the green (exists) |
| Scrape | Leatherworking | One pass, tap as each patch goes by (exists) |
| Thread the needle | Weaving | Tap when two bobbing marks line up (exists) |
| **Cut** | Leatherworking | Hold to draw the blade along the pattern; release on the end mark. Closer is better. |
| **Spin** | Weaving | Keep the wheel's speed inside a band by tapping; too slow and the yarn slubs, too fast and it snaps. |
| **Sew** | Weaving | Alternate left and right clicks on the beat: needle in, needle out. |
| **Carve** | Woodworking | The knife drifts off the grain line; tap to nudge it back, staying in the band to the end. |
| **Turn** | Woodworking | Hold to press the chisel; a gauge rises; release in the green before it bites too deep. Two or three passes. |

## Forging

Unchanged apart from the Guild Ledger: heat → strike → quench → grind → parts → gear.

- **Timed:** heating bars. **★ steps:** strike, grind. **★ cap:** flux.
- **Workshop levels:** as today (Blast Furnace and Master Anvil for 2; Bellows, Oil Cauldron and Grinding Wheel for 3).
- **Dungeon materials:** the tier ingots (Thraggite, Wardsilver, Emberstone, Thailite), Borax, Runic Shards.
- **New:** forged swords and axes need a **grip** (Leatherworking) or a **haft** (Woodworking).

## Leatherworking

The tanner's process: tan liquor → flesh → tan → cut → finish.

| Step | Station | In → out | Time | ★ |
|---|---|---|---|---|
| 1. Brew liquor | Cooking Pot (Farmer's Delight), heated | Water bucket + 4 Tree Bark → **Bark Liquor** (3 uses, bottled) | About 20 s | Liquor caps it |
| 2. Flesh | Tanning Rack (exists) | Leather → **Scraped Hide** (today's scrape) | — | Sets it |
| 3. Tan | **Tanning Vat**: pour liquor into a cauldron (as quench oil does), no heat | Up to 4 Scraped Hides → **Tanned Leather** | About 2 min | — |
| 4. Cut | **Shaping Rack**, shears or a knife in hand | Tanned Leather → **Leather Panels** (2), or + string → **Straps** (4) | — | Sets it |
| 5. Finish | Crafting | Panels + honeycomb → **Hardened Leather** (as today) | — | Keeps it |
| 6. Assemble | Crafting table (starred recipes) | Light and medium armor, **Grips**, **Quivers**, **Leather Backings** | — | Lowest part |

- **Liquor tiers (the ★ cap):** Bark Liquor ★3; **Tannin Liquor** (+ Barrowmoss, an undead dungeon herb) ★5;
  **Elder Liquor** (+ Emberroot, from tier 3 dungeons) ★7.
- **Hide tiers:** vanilla leather and rabbit hide (tiers 1–2); **Warg Pelt** (orc dungeons) and **Gloomhide** (tier 3
  undead) for the best armor; they take the same steps.
- **Workshop levels:** 1: Tanning Rack, Shaping Rack, a cauldron. 2: a **Tanning Barrel** (barrel model; a vat that
  holds 8 hides and tans 25% faster). 3: a **Fleshing Beam** (a stripped-log beam; a wider scrape patch).
- **Pulls from:** combat (hides), Woodcutting (bark), Farming (honeycomb, and heat for the pot), string.
- **XP:** a clean scrape 3, a tan 4 per hide, a cut 3, a crafted piece 15 × ★.

## Weaving (Tailoring)

The weaver's process: fibre → spin → weave → full → cut and sew.

| Step | Station | In → out | Time | ★ |
|---|---|---|---|---|
| 1. Fibre | Shears on sheep, cobwebs, Farmer's Delight straw | Wool, string, Canvas | — | — |
| 2. Spin | **Spinning Wheel** | Wool → **Yarn** (3); string is thread as it is | — | Thread caps it |
| 3. Weave | Loom (exists) | 3 Yarn or string → **Woven Cloth** (today's) | — | Sets it |
| 4. Full | Water cauldron over heat | Up to 4 Woven Cloth → **Fulled Cloth** (today's padded cloth); add a dye first to color it | About 1 min | — |
| 5. Cut and sew | Shaping Rack, shears or a knife, string in the off hand | Fulled Cloth → **Cloth Panels** (2) | — | Sets it |
| 6. Assemble | Crafting table | Robes, hoods, gambesons, **Gambeson Backings**, **Bowstrings**, staff wraps | — | Lowest part |

- **Coarse tier:** Canvas (Farmer's Delight) goes straight to the Shaping Rack for sacks, backings and cheap
  padding, ★1–2.
- **Thread tiers (the ★ cap):** wool yarn ★3; **Silk Thread** (from dungeon spiders in goblin caves) ★5; **Spellsilk**
  (silk thread steeped in a brewing stand with lapis and Arcane Dust from dungeon casters; a second timed step) ★7, the
  base for the best mage robes.
- **Workshop levels:** 1: Spinning Wheel, loom, Shaping Rack, a heated cauldron. 2: a **Great Loom** (wider needle
  eye). 3: a **Dye Works** (a cauldron rack; fulls 8 cloth at once and keeps more ★ on dyed cloth).
- **Pulls from:** sheep, spiders, Farming (dye plants, straw), Mining (coal or magma for heat).
- **XP:** a spun yarn 1, a stitch 1 and a cloth 5, a fulling 2 per cloth, a sewn panel 3, a crafted piece 15 × ★.

## Woodworking (new)

The woodworker's process: fell → season → rough out → shape → bend → finish → fit.

| Step | Station | In → out | Time | ★ |
|---|---|---|---|---|
| 1. Fell | Woodcutting | Logs; the wood matters (spruce for bows, oak for staves, dungeon woods for the best) | — | — |
| 2. Season | Smoker | Log → **Seasoned Billet** | About 30 s (twice food's) | — |
| 3. Rough out | **Chopping Block** (a stripped log with an axe stuck in it), axe in hand | Billet → **Bow Stave** (2), **Staff Blank**, **Hafts** (3), **Stock** | — | — |
| 4a. Carve | Fletching table | Bow Stave → **Bow Limbs** | — | Sets it |
| 4b. Turn | **Wood Lathe** (a stonecutter-style body with a spindle) | Staff Blank → **Staff Shaft**; a haft → **Wand Core** | — | Sets it |
| 5. Bend (bows, crossbows) | Water cauldron over heat | Up to 4 limbs → **Bent Limbs** | About 1 min | — |
| 6. Finish | Crafting with a wood finish | Any part + finish → finished part. The finish caps the ★. | — | Finish caps it |
| 7. Assemble | Crafting table | Bows (bent limbs + bowstring), crossbows (stock + limbs + fittings), staves (shaft + focus + wrap), wands (core + focus), shields (boards + rim + straps), polearm hafts | — | Lowest part |

- **Finish tiers (the ★ cap):** **Linseed Oil** (wheat seeds in a brewing stand) ★3; **Heartwood Resin** (dryad
  groves) ★5; **Sylvan Sap** (deep dryad groves) ★7.
- **Wood tiers:** vanilla woods (★ up to 5); **Heartwood** and **Ironbark** from dryad groves for the best bows and
  staves.
- **Staff foci:** a crystal or gem: Runic Shards and dungeon crystals now; cut gems once Jewelcrafting exists.
- **Workshop levels:** 1: smoker, Chopping Block, fletching table, Wood Lathe, a heated cauldron. 2: a **Treadle
  Lathe** (wider green on the gauge). 3: a **Bowyer's Bench** (a wider carving band).
- **Pulls from:** Woodcutting (logs), Farming (seeds for oil), Weaving (bowstrings, staff wraps), Forging (crossbow
  fittings, shield rims, ferrules), dungeons (foci, dryad woods).
- **XP:** a seasoned billet 2, a split 1, a carve or turn 5, a bend 2 per limb, a crafted piece 15 × ★.
- **A Woodworking skill tree** joins the crafting trees, in the same shape as Forging's.

### The dryads (a neutral faction)

- **In the overworld:** dryads live in old forests and don't attack, unless you cut their grove's trees or attack
  them.
- **Their dungeons are groves:** exploring, puzzles (bloom the right flowers, guide light through the canopy) and trades
  with the dryads, guarded by wild beasts and corrupted treants that you do fight.
- **Rewards:** Heartwood, Ironbark, Heartwood Resin and Sylvan Sap, by tier.
- **Its own design pass:** mobs, groves, layouts and the dungeon tie-in are a separate piece of work.

## Cross-crafting

| Gear | Parts |
|---|---|
| Forged swords and axes | Blade (Forging) + Grip (Leatherworking) or Haft (Woodworking) |
| Plate and mail | Plates or links (Forging) + Leather or Gambeson Backing |
| Brigandine | Hardened leather and rivets + Gambeson Backing |
| Bows | Bent Limbs (Woodworking) + Bowstring (Weaving) |
| Crossbows | Stock and Bent Limbs (Woodworking) + Fittings (Forging) |
| Staves and wands | Shaft or core (Woodworking) + focus + Staff Wrap (Weaving) |
| Shields | Boards (Woodworking) + Rim (Forging) + Straps (Leatherworking) |
| Robes and hoods | Cloth Panels (Weaving) |
| Light and medium armor | Leather Panels and Straps (Leatherworking), mail links for medium (Forging) |

A top bow needs a skilled woodworker and a skilled weaver, or one player who trained both.

## New blocks and items

- **Blocks:** Guild Ledger; Shaping Rack; Spinning Wheel; Chopping Block; Wood Lathe; Tanning Vat (a filled cauldron)
  and, for the upgrades, Tanning Barrel, Fleshing Beam, Great Loom, Dye Works, Treadle Lathe, Bowyer's Bench.
- **Models:** reuse vanilla parts and textures (lectern, barrel, grindstone wheel, stonecutter, stripped logs,
  loom), with new textures only where needed.
- **Items:** Bark Liquor (and Tannin, Elder), Scraped Hide, Tanned Leather, Leather Panels, Straps, Grip, Yarn, Silk
  Thread, Spellsilk, Cloth Panels, Bowstring, Staff Wrap, Seasoned Billet, Bow Stave, Staff Blank, Haft, Stock, Bow
  Limbs, Bent Limbs, Staff Shaft, Wand Core, Linseed Oil, Heartwood Resin, Sylvan Sap, plus the dungeon materials.

## Build phases

| Phase | What | Depends on |
|---|---|---|
| 1 | Farmer's Delight in the pack; Guild Ledger replaces the hearth; per-profession workshop levels and decor | — |
| 2 | Leatherworking rebuilt (liquor, tanning vat, Shaping Rack, cutting) | 1 |
| 3 | Weaving rebuilt (Spinning Wheel, fulling, sewing) | 1, shares the Shaping Rack with 2 |
| 4 | Woodworking (stations, minigames, skill and tree) | 1 |
| 5 | Cross-crafting: grips, hafts, bows, crossbows, staves, wands, shields; the RPG recipe sweep updated to use them | 2–4 |
| 6 | Dungeon materials (herbs, hides, silk, essences) in the existing factions' dungeons | 2–4 |
| 7 | The dryad faction and its groves | 4 |
| Later | Jewelcrafting; how crafting covers the RPG Series' many weapons | — |

## Questions for review

1. The minigame list: does each feel distinct enough, and is any a bad fit?
2. The workshop upgrades (Tanning Barrel, Fleshing Beam, Great Loom, Dye Works, Treadle Lathe, Bowyer's Bench): right
   kind of upgrade, or should levels come from something else?
3. Cross-crafting makes good gear a group or multi-skill effort. Is that the right amount of dependence?
4. Build order: start with phase 1 and 2?
