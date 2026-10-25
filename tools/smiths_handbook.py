"""Writes The Smith's Handbook, the Patchouli guide to forging. tools/profession_resources.py adds the other crafts' pages.

Run from the project root: python tools/smiths_handbook.py
The book is defined in data/ and its pages live in assets/ (use_resource_pack). Text uses Patchouli's formatting:
$(item)...$() highlights, $(br) breaks a line, $(br2) leaves a blank line, $(l:<category>/<entry>)...$() links.
"""
import json
from pathlib import Path

MOD = "dungeondelving"
BOOK = "smiths_handbook"
DATA = Path("src/main/resources/data") / MOD
BOOK_ASSETS = Path("src/main/resources/assets") / MOD / "patchouli_books" / BOOK / "en_us"


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def text(body, title=None):
    page = {"type": "patchouli:text", "text": body}
    if title:
        page["title"] = title
    return page


def spotlight(item, body, title=None):
    page = {"type": "patchouli:spotlight", "item": item, "text": body}
    if title:
        page["title"] = title
    return page


def crafting(recipe, body=None, recipe2=None):
    page = {"type": "patchouli:crafting", "recipe": f"{MOD}:{recipe}"}
    if recipe2:
        page["recipe2"] = f"{MOD}:{recipe2}"
    if body:
        page["text"] = body
    return page


def smelting(recipe, body=None):
    page = {"type": "patchouli:smelting", "recipe": f"{MOD}:{recipe}"}
    if body:
        page["text"] = body
    return page


CATEGORIES = {
    "forge": ("The Forge", "Building a forge, and the upgrades that let it work finer metal.", f"{MOD}:guild_ledger", 0),
    "smithing": ("Smithing", "Heating, striking, quenching and grinding iron into tempered ingots.", f"{MOD}:tempered_iron_ingot", 1),
    "parts": ("Flux and Parts", "Turning tempered ingots into plates, rivets and chain.", f"{MOD}:armor_plate", 2),
    "gear": ("Forged Gear", "Forged iron, diamond and netherite weapons and armor, and what their stars mean.", "minecraft:iron_chestplate", 7),
    "skills": ("Crafting Skills", "Forging, Leatherworking, Weaving and Woodworking, and what practice earns you.", "minecraft:experience_bottle", 8),
}

ENTRIES = {
    "forge": {
        "building": ("Building a Forge", f"{MOD}:guild_ledger", [
            spotlight(f"{MOD}:guild_ledger", "Every workshop starts with a $(item)Guild Ledger$(). A forge needs four stations within 7 "
                      "blocks of it across, and 4 up or down, set out however you like:", "The Guild Ledger"),
            text("$(li)a $(item)furnace$() or $(item)blast furnace$(), to heat metal"
                 "$(li)an $(item)anvil$(), to strike it"
                 "$(li)a $(item)cauldron$() of water (or oil), to quench it"
                 "$(li)a $(item)grindstone$(), to finish it"
                 "$(br2)Read the ledger to see what's missing. Striking, quenching and grinding only work at stations near a ledger "
                 "with a complete forge. One ledger serves every craft in its range."),
            crafting("guild_ledger"),
            text("Decoration counts too. Bricks, stone and deepslate bricks, chains, lanterns, iron bars, barrels and the like near "
                 "the ledger widen every sweet spot a little: +0.5% for every 8 blocks, up to +2.5%. A forge that looks like a forge "
                 "works like one.", "A Proper Workshop"),
        ]),
        "levels": ("Forge Levels", "minecraft:blast_furnace", [
            text("A forge's level caps how fine a piece it can work, in stars ($(item)★$()):"
                 "$(li)$(bold)Level 1$(): the basic stations. Up to ★3."
                 "$(li)$(bold)Level 2$(): a $(item)Blast Furnace$() and a $(item)Master Anvil$(). Up to ★5."
                 "$(li)$(bold)Level 3$(): also $(item)Bellows$() by a furnace, an $(item)Oil Cauldron$() and a $(item)Grinding Wheel$(). Up to ★7.",
                 "Forge Levels"),
            text("The basic anvil and grindstone also wear on your work: one in ten pieces finished on them spoils, however well you "
                 "struck it. The Master Anvil and Grinding Wheel never do."),
        ]),
        "upgrades": ("Station Upgrades", f"{MOD}:master_anvil", [
            spotlight(f"{MOD}:bellows", "Set next to a furnace or blast furnace. Each pump (right-click) pushes along whatever it's heating.", "Bellows"),
            crafting("bellows"),
            spotlight(f"{MOD}:master_anvil", "Widens the sweet spot when striking, never wears on your work, and never falls. Forged from "
                      "Wardsilver, found in tier 2 dungeons.", "Master Anvil"),
            crafting("master_anvil"),
            spotlight(f"{MOD}:quenching_oil", "Pour bottles of Quenching Oil into a cauldron (up to three) to make an $(item)Oil Cauldron$(). "
                      "Quenching in oil never cracks the metal.", "Oil Cauldron"),
            crafting("quenching_oil"),
            spotlight(f"{MOD}:grinding_wheel", "A finer grindstone: a wider sweet spot, and it never spoils your work. Built with Emberstone "
                      "from tier 3 dungeons.", "Grinding Wheel"),
            crafting("grinding_wheel"),
        ]),
    },
    "smithing": {
        "heating": ("Heating", f"{MOD}:heated_ingot", [
            smelting("heating/iron_smelting", "Smelt an iron ingot to get a $(item)Heated Ingot$(). A blast furnace is twice as fast."),
            text("Finer metal takes longer: heating an ingot takes 30 seconds more for each ★ it's aiming for past the first (15 in a "
                 "blast furnace). Iron heats in 5 seconds; a Tempered Ingot ★4, aiming for ★5, takes 2 minutes 5 seconds.", "Heating Time"),
            text("Metal starts cooling once it leaves the furnace: orange, then red, then faded red, then cold. You have about 30 "
                 "seconds to work it (longer as your Forging improves). Cooler metal is harder to strike: the marker sweeps faster. "
                 "Cold metal goes back in the furnace with no loss.", "Cooling"),
        ]),
        "striking": ("Striking", "minecraft:anvil", [
            text("Hold the hot ingot and right-click the anvil. A bar appears, with a marker sweeping back and forth. Each fresh "
                 "right-click is one strike: aim for the $(2)green$() sweet spot, or better, its $(6)gold$() middle."
                 "$(br2)Three strikes shape an ingot (more for higher stars). Gold strikes count twice and are always safe.", "The Bar"),
            text("A strike outside the green might crack the ingot: 30% for the first miss in a bar, 45% for the second, and so on. "
                 "Cracked metal leaves iron nuggets. Your Forging skill lowers the odds."
                 "$(br2)The result is a $(item)Struck Ingot$(), still hot. Quench it before it cools.", "Misses"),
        ]),
        "quenching": ("Quenching", "minecraft:cauldron", [
            text("Right-click a cauldron with the hot struck ingot. Water quenches, but cracks the metal 30% of the time, however "
                 "skilled you are. Oil never does. Each quench uses a level of water or oil; refill the cauldron when it's empty."
                 "$(br2)You get a $(item)Quenched Ingot$().", "Quenching"),
        ]),
        "grinding": ("Grinding", "minecraft:grindstone", [
            text("Hold the quenched ingot and right-click the grindstone: the same bar, two passes. Get both clean and you have a "
                 "$(item)Tempered Iron Ingot$(). Miss either and it comes out an $(item)Unstable Ingot$(): grind it again, cleanly, "
                 "or it breaks.", "Grinding"),
            spotlight(f"{MOD}:tempered_iron_ingot", "The finished metal. Its stars say how many times it's been through the forge.", "Tempered Iron"),
        ]),
        "stars": ("Raising the Stars", f"{MOD}:tempered_iron_ingot", [
            text("Put a Tempered Ingot ★ back in the furnace and it comes out aiming one star higher. Each pass is riskier: more "
                 "strikes, a narrower sweet spot.$(br2)Your forge's level and your Forging skill both cap how high you can go:"
                 "$(li)★2 at Forging 3, ★3 at 6, ★4 at 10"
                 "$(li)★5 at 15, ★6 at 20, ★7 at 25", "Raising the Stars"),
        ]),
    },
    "parts": {
        "flux": ("Flux", f"{MOD}:sand_flux", [
            text("Flux keeps metal clean as it's joined. Every part needs some, and better parts need better flux: a part is never "
                 "better than its flux allows, whatever its ingots."
                 "$(li)$(item)Sand Flux$(): up to ★3"
                 "$(li)$(item)Borax Flux$(): up to ★5"
                 "$(li)$(item)Runic Flux$(): ★6"
                 "$(li)$(item)Runeforged Flux$(): ★7", "Flux"),
            crafting("sand_flux", recipe2="borax_flux"),
            text("$(item)Borax$() is mined from Borax Deposits in tier 2 dungeons and deeper. Runic Flux needs a Runic Shard III; "
                 "Runeforged Flux needs Thailite from tier 4.", "Borax"),
            crafting("runic_flux", recipe2="runeforged_flux"),
        ]),
        "parts": ("Parts", f"{MOD}:armor_plate", [
            text("Parts are made at a crafting table from tempered ingots and flux. A part takes the worst star among its ingots.",
                 "Parts"),
            crafting("armor_plate", "An ingot and flux make an Armor Plate."),
            crafting("rivets", "An ingot, flux and a nugget make four Rivets."),
            crafting("chain_links", "Two ingots and flux make eight Chain Links."),
        ]),
    },
    "gear": {
        "forged": ("Forged Gear", "minecraft:iron_sword", [
            text("Any iron, diamond or netherite weapon or armor can be forged, and a forged piece is always stronger than a plain "
                 "one. It keeps its usual look; its ★ shows on its name. A plain piece (crafted the usual way, or found) has no ★.",
                 "Forged Gear"),
            text("The ★ climbs with the material:$(li)$(bold)Iron$(): ★1 to ★3. The usual shape, in forged plates; the worst plate "
                 "sets the ★.$(li)$(bold)Diamond$(): ★4 or ★5. A forged iron piece and a diamond at a smithing table; ★5 from a ★3 "
                 "piece.$(li)$(bold)Netherite$(): ★6 or ★7. A forged diamond piece, the usual way; ★7 from ★5.", "The Stars"),
            text("Each ★ into its material adds 5% to the piece's damage or armor (and the like), and 10% to its durability. A ★3 "
                 "iron sword is 15% better than a plain one; a ★4 diamond sword 5% better than a plain diamond one, and so on.",
                 "How Much Better"),
            crafting("forged/iron_sword", recipe2="forged/iron_chestplate"),
        ]),
        "apotheosis": ("Stars and Apotheosis", "minecraft:enchanting_table", [
            text("With Apotheosis, stars go further: ★4 gear has a gem socket and ★5 two; ★6 and ★7 strengthen the attribute bonuses "
                 "Apotheosis puts on them; and ★7 gear comes off the table already enchanted with an Apotheosis rarity.", "Apotheosis"),
        ]),
        "upkeep": ("Repair", "minecraft:anvil", [
            text("Repair forged gear at an anvil, as you would a plain piece. It keeps its ★.", "Repair"),
        ]),
    },
    "skills": {
        "skills": ("Crafting Skills", "minecraft:experience_bottle", [
            text("Forging, Leatherworking, Weaving and Woodworking level as you practice, with no limit. Use $(item)/skills$() to "
                 "see where you stand, and the skill trees (K) to spend the points each level gives.$(br2)Your level sets the best ★ "
                 "you can make: ★2 from level 3, ★3 from 6, ★4 from 10, ★5 from 15, ★6 from 20, ★7 from 25. The trees' perks widen "
                 "sweet spots and lower the odds of ruining a piece.", "Crafting Skills"),
        ]),
    },
}


def main():
    write(DATA / "patchouli_books" / BOOK / "book.json", {
        "name": "The Smith's Handbook",
        "landing_text": "A guide to the forge, the tannery and the loom: how to work iron into tempered steel, leather into armor, "
                        "and string into cloth, and how practice makes a master.",
        "use_resource_pack": True,
        "version": "1",
        "creative_tab": f"{MOD}:dungeon_delving",
        "model": "patchouli:book_brown",
        "show_progress": False,
        "text_overflow_mode": "resize",
    })
    for category, (name, description, icon, sort) in CATEGORIES.items():
        write(BOOK_ASSETS / "categories" / f"{category}.json", {"name": name, "description": description, "icon": icon, "sortnum": sort})
        for order, (entry, (title, entry_icon, pages)) in enumerate(ENTRIES[category].items()):
            write(BOOK_ASSETS / "entries" / category / f"{entry}.json", {
                "name": title, "icon": entry_icon, "category": f"{MOD}:{category}", "sortnum": order, "pages": pages,
            })
    # The book itself: a book and an iron ingot. Only when Patchouli is installed.
    write(DATA / "recipe" / f"{BOOK}.json", {
        "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "patchouli"}],
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "minecraft:book"}, {"item": "minecraft:iron_ingot"}],
        "result": {"id": "patchouli:guide_book", "count": 1, "components": {"patchouli:book": f"{MOD}:{BOOK}"}},
    })
    print("smith's handbook written")


if __name__ == "__main__":
    main()
