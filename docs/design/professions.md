# Crafting professions

The plan for the crafting professions: a shared workshop, Forging as it is, Leatherworking and Weaving rebuilt,
Woodworking new. Agreed direction 2026-10-24, reworked 2026-10-25 after the first playtest; numbers wait for more
playtesting.

**Decided:** the shared block is the **Guild Ledger**. New stations reuse vanilla models where they can, but new ones
are fine. **Farmer's Delight** joins the pack for its bark, straw, canvas, knives and cooking pot. Dryad dungeons are
**mixed**: groves to explore, with puzzles, trades and fights.

## Built so far (2026-10-25)

- **Phase 1:** the Guild Ledger (replacing the Forge Hearth) and Farmer's Delight.
- **Phases 2–4, the core:** Leatherworking, Weaving and Woodworking, with their stations, workshop levels and upgrades;
  the Woodworking skill and tree; bows and crossbows from parts. RPG and vanilla gear keep their own recipes
  (DD-89): Dungeon Delving's forged gear is the one forged set for now.
- **Reworked after the first playtest (2026-10-25):** every craft now follows Forging's pattern (below). The scraping,
  spinning, sewing and carving minigames, fulling, bending and the Chopping Block are gone.
- **Not yet:** sources for the dungeon materials (Barrowmoss, Emberroot, spider silk, Arcane Dust; phase 6) and the
  dryads' woods and finishes (phase 7); dyeing at the Dye Works; real art for the new blocks and items (placeholders and
  vanilla models for now); RPG staves, wands and shields from parts (phase 5); the Woodworking tree's Seasoner, Fine
  Split and Master Bowyer perks; forged gear for more weapon and armor classes, and variant recipes for specific looks.

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
| Tree Bark (a log chopped on its Cutting Board with an axe, which also gives the stripped log) | Tanning liquor |
| Straw (harvesting with a knife) → Canvas (4 straw) | The plant fibre for Weaving's coarse tier |
| Rope | Bindings, and the Shaping Rack's recipe |
| Knives (flint to netherite; the `c:tools/knife` tag) | The cutting tool at the Shaping Rack, beside shears |
| Cooking Pot (a timed, heated pot) | Brewing the tanning liquor |
| Rice, cabbage, tomatoes, onions | Cooking, and more Farming XP sources |

Checked against Farmer's Delight 1.21.1-1.3.4 (`farmersdelight:tree_bark`, `straw`, `canvas`, `rope`, `cooking_pot`, `cutting_board`).

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

Every craft follows Forging's pattern:

1. **Prepare** the vanilla material (no minigame): leather → Scraped Hide, wool → Yarn, log → Seasoned Billet. Forging's
   is the iron ingot itself.
2. **Timed step, which sets the ★:** heating for metal; a vat for the rest. The liquid's tier is the piece's ★, as the
   heated ingot's target is. Each ★ takes **30 seconds longer** (heating: +30 s per ★ past the first, half that in a
   blast furnace; a vat: 1:00 plus 30 s per ★, so ★3 2:00, ★5 3:00, ★7 4:00).
3. **Bench minigame:** keeps the ★; the minigame decides whether the piece survives (misses can ruin it, likelier with
   each one). Like the anvil, the bench refuses a piece above the workshop's level or the player's skill, and a vat
   refuses a liquid above the workshop's level.
4. **Craft:** parts and gear, each taking the lowest ★ of its parts.

| Craft | Prepare | Timed (sets ★) | Bench minigame | Gives |
|---|---|---|---|---|
| Forging | Iron ingot | Heat in a furnace | Strike, quench, grind | Tempered Ingot |
| Leatherworking | Scrape at the Tanning Rack | Tanning Vat (liquor) | Cut at the Shaping Rack | 2 Leather Panels |
| Weaving | Spin at the Spinning Wheel | Sizing Vat (sizing) | Thread the needle at the loom | 2 Cloth Panels |
| Woodworking | Season in a smoker | Finishing Vat (finish) | Turn at the lathe | 2 Staves |

Each vat is a cauldron with the liquid poured in; it holds four pieces a batch. The upgrades with a bigger vat (the
Tanning Barrel, the Dye Works) hold eight and work a quarter faster.

### The minigames

| Minigame | Profession | Input |
|---|---|---|
| Strike, Grind | Forging | Tap when the sweeping marker is in the green |
| Thread the needle | Weaving | Tap when two bobbing marks line up |
| Cut | Leatherworking | Hold to draw the blade along the pattern; release on the end mark |
| Turn | Woodworking | Hold to press the chisel; release in the green before it bites too deep. Three passes |

## Forging

Unchanged apart from the Guild Ledger and heating time: heat → strike → quench → grind → parts → gear.

- **Timed:** heating, 5 s for iron and 30 s more for each ★ past the first. **★:** each pass through the forge raises
  it by one. **★ cap:** flux, the workshop and the skill.
- **Workshop levels:** as today (Blast Furnace and Master Anvil for 2; Bellows, Oil Cauldron and Grinding Wheel for 3).
- **Dungeon materials:** the tier ingots (Thraggite, Wardsilver, Emberstone, Thailite), Borax, Runic Shards.
- **Later:** forged swords and axes need a **grip** (Leatherworking) or a **haft** (Woodworking).

## Leatherworking

Scrape → tan → cut → craft.

| Step | Station | In → out | Time | ★ |
|---|---|---|---|---|
| 1. Scrape | Tanning Rack | Leather → **Scraped Hide** | — | — |
| 2. Tan | **Tanning Vat** (liquor poured into a cauldron) | Up to 4 hides → **Tanned Leather** | 2:00–4:00 | The liquor's |
| 3. Cut | **Shaping Rack**, shears or a knife | Tanned Leather → **Leather Panels** (2) | — | Keeps it |
| 4. Craft | Crafting table | Straps, Hardened Leather (+ honeycomb), Leather Backings | — | Lowest part |

- **Liquors (brewed in a cooking pot):** Bark Liquor (4 tree bark) ★3; **Tannin Liquor** (+ Barrowmoss) ★5; **Elder
  Liquor** (+ Emberroot) ★7.
- **Workshop levels:** 1: Tanning Rack, Shaping Rack, a cauldron. 2: a **Tanning Barrel** (a vat for 8, 25% faster).
  3: a **Fleshing Beam** (a wider cutting mark).
- **XP:** a scrape 1, a tan 2 × ★ per hide, a cut 5 × ★.

## Weaving (Tailoring)

Spin → size → weave → craft.

| Step | Station | In → out | Time | ★ |
|---|---|---|---|---|
| 1. Spin | **Spinning Wheel** | Wool → **Yarn** (3) | — | — |
| 2. Size | **Sizing Vat** (sizing poured into a cauldron) | Up to 4 yarn or string → **Sized Yarn** | 2:00–4:00 | The sizing's |
| 3. Weave | Loom | 3 Sized Yarn → **Cloth Panels** (2) | — | Keeps it |
| 4. Craft | Crafting table | Gambeson Backings, staff wraps, Bowstrings (3 sized yarn) | — | Lowest part |

- **Sizings (brewed in a cooking pot):** Wheat Sizing (4 wheat) ★3; **Silk Sizing** (+ spider silk) ★5; **Arcane
  Sizing** (+ Arcane Dust) ★7.
- **Workshop levels:** 1: Spinning Wheel, loom, a cauldron. 2: a **Great Loom** (wider needle eye). 3: the **Dye
  Works** (a sizing vat for 8, 25% faster; dyeing later).
- **XP:** a spin 1, sizing 1 × ★ per yarn, a weave 5 × ★.

## Goldsmithing (first pass, DD-89)

Smelt → refine → chase → craft.

| Step | Station | In → out | Time | ★ |
|---|---|---|---|---|
| 1. Smelt | Furnace | Raw gold → gold ingot (vanilla) | — | — |
| 2. Refine | **Crucible** (a refining salt put in) | Up to 4 gold ingots → **Fine Gold** | 2:00–4:00 | The salt's |
| 3. Chase | **Jeweler's Bench** | Fine Gold → **Gold Fittings** (2); a sweeping marker, three strikes | — | Keeps it |
| 4. Craft | Crafting table | **Gold Ring** (+1 max health a ★), **Gold Amulet** (+0.5 armor a ★), in Curios slots; **gilded** armor and sword | — | Lowest part |

- **Salts:** Bone Ash (bone meal, charcoal) ★3; **Assay Salt** (+ borax) ★5; **Sunsalt** (+ Arcane Dust) ★7.
- **Gilded gear:** the golden shapes in fittings. A little less armor than chain, no weight, enchants like gold, and
  piglins take it for gold. Repaired with fine gold.
- **Workshop levels:** 1: furnace, Crucible, Jeweler's Bench. 2: a **Great Crucible** (8, 25% faster). 3: a **Jeweler's
  Loupe** (a wider sweet spot at the bench).
- **Later (DD-91):** cut gems and settings, staff foci, more jewelry.

## Weaponsmithing and Armorsmithing (DD-89)

Forging is two skills now, so a smith can be good at both by learning both. The forge work (heat, strike, quench,
grind) trains both and goes as high as the better of the two. Blades (an ingot and flux) and axe heads (two ingots and
flux) and the forged sword and axe train and are capped by Weaponsmithing; plates, rivets, chain links and plate armor
by Armorsmithing. Each has its own tree: the anvil's perks in Weaponsmithing's, the grindstone's and the metal's in
Armorsmithing's. Old Forging XP moved to both, and its perks were refunded.

## Woodworking (new)

Season → cure → turn → craft.

| Step | Station | In → out | Time | ★ |
|---|---|---|---|---|
| 1. Season | Smoker | Log → **Seasoned Billet** | About 30 s | — |
| 2. Cure | **Finishing Vat** (a finish poured into a cauldron) | Up to 4 billets → **Cured Billets** | 2:00–4:00 | The finish's |
| 3. Turn | **Wood Lathe** | Cured Billet → **Staves** (2) | — | Keeps it |
| 4. Craft | Crafting table | Bow Limbs (2 staves → 2), Hafts (1 stave → 2), Staff Shaft (2 staves), Wand Core (a haft), Crossbow Stock (2 staves); bows (2 limbs, a haft, a bowstring) and crossbows (2 limbs, iron, a stock, a bowstring) | — | Lowest part |

- **Finishes:** **Linseed Oil** (4 wheat seeds in a cooking pot) ★3; **Heartwood Resin** (dryad groves) ★5; **Sylvan
  Sap** (deep dryad groves) ★7.
- **Staff foci:** a crystal or gem: Runic Shards and dungeon crystals now; cut gems once Jewelcrafting exists.
- **Workshop levels:** 1: smoker, Wood Lathe, a cauldron. 2: a **Treadle Lathe** (wider green). 3: a **Bowyer's
  Bench** (wider green again).
- **XP:** a cure 2 × ★ per billet, a turn 5 × ★.
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

- **Blocks:** Guild Ledger; Shaping Rack; Spinning Wheel; Wood Lathe; the Tanning, Sizing and Finishing Vats (filled
  cauldrons) and, for the upgrades, Tanning Barrel, Fleshing Beam, Great Loom, Dye Works, Treadle Lathe, Bowyer's Bench.
- **Models:** reuse vanilla parts and textures (lectern, barrel, grindstone wheel, stonecutter, stripped logs,
  loom), with new textures only where needed.
- **Items:** the liquors (Bark, Tannin, Elder), sizings (Wheat, Silk, Arcane) and finishes (Linseed Oil, Heartwood
  Resin, Sylvan Sap); Scraped Hide, Tanned Leather, Leather Panels, Straps; Yarn, Sized Yarn, Cloth Panels, Bowstring,
  Staff Wrap; Seasoned Billet, Cured Billet, Stave, Haft, Stock, Bow Limbs, Staff Shaft, Wand Core; plus the dungeon
  materials.

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
