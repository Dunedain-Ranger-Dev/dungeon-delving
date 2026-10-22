"""Writes The Smith's Handbook, the Patchouli guide to forging, leatherworking and weaving.

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
    "forge": ("The Forge", "Building a forge, and the upgrades that let it work finer metal.", f"{MOD}:forge_hearth", 0),
    "smithing": ("Smithing", "Heating, striking, quenching and grinding iron into tempered ingots.", f"{MOD}:tempered_iron_ingot", 1),
    "parts": ("Flux and Parts", "Turning tempered ingots into plates, rivets and chain.", f"{MOD}:armor_plate", 2),
    "leather_cloth": ("Leather and Cloth", "Scraping and hardening leather, and weaving cloth.", f"{MOD}:hardened_leather", 3),
    "gear": ("Forged Gear", "The armor and weapons a smith can make, and keeping them in repair.", f"{MOD}:plate_chestplate", 4),
    "skills": ("Crafting Skills", "Forging, Leatherworking and Weaving, and what practice earns you.", "minecraft:experience_bottle", 5),
}

ENTRIES = {
    "forge": {
        "building": ("Building a Forge", f"{MOD}:forge_hearth", [
            spotlight(f"{MOD}:forge_hearth", "Every forge starts with a $(item)Forge Hearth$(). Around it, within 7 blocks across and 4 up or down, "
                      "it needs four stations, set out however you like:", "The Hearth"),
            text("$(li)a $(item)furnace$() or $(item)blast furnace$(), to heat metal"
                 "$(li)an $(item)anvil$(), to strike it"
                 "$(li)a $(item)cauldron$() of water (or oil), to quench it"
                 "$(li)a $(item)grindstone$(), to finish it"
                 "$(br2)Right-click the hearth to see what's missing. Its fire lights once the forge is complete; striking, quenching "
                 "and grinding only work at stations near a lit hearth."),
            crafting("forge_hearth"),
            text("Decoration counts too. Bricks, stone and deepslate bricks, chains, lanterns, iron bars, barrels and the like near "
                 "the hearth widen every sweet spot a little: +0.5% for every 8 blocks, up to +2.5%. A forge that looks like a forge "
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
    "leather_cloth": {
        "scraping": ("Scraping Leather", f"{MOD}:tanning_rack", [
            spotlight(f"{MOD}:tanning_rack", "Hold leather and right-click a $(item)Tanning Rack$(). The scraper makes one pass across "
                      "the hide; click as it crosses each dark patch, in order.", "The Tanning Rack"),
            text("A patch missed (or passed by) might tear the hide, likelier with each miss, leaving only $(item)Leather Scraps$() "
                 "(four make a leather again). Scrape it clean and you have $(item)Refined Leather$().", "Scraping"),
            crafting("tanning_rack", recipe2="leather_from_scraps"),
        ]),
        "hardening": ("Hardening Leather", f"{MOD}:hardened_leather", [
            text("Hold refined leather and right-click a water cauldron with heat under it: a lit campfire, fire, magma or lava. "
                 "It boils into $(item)Boiled Leather$(), using a level of water.", "Boiling"),
            crafting("hardened_leather", "Wax it with honeycomb and stitch it with string."),
            crafting("leather_base", "The backing medium and heavy armor is built on."),
        ]),
        "weaving": ("Weaving", "minecraft:loom", [
            text("Hold three string and right-click a loom. The needle's eye bobs up and down, and so does the thread's tip, at a "
                 "different pace. Click when the thread lines up with the eye. Three stitches make a $(item)Woven Cloth$()."
                 "$(br2)A missed stitch might snap the thread (likelier with each miss), saving just one string.", "Thread the Needle"),
            crafting("padded_cloth", recipe2="gambeson_base"),
        ]),
    },
    "gear": {
        "weights": ("Three Weights", f"{MOD}:hauberk_chestplate", [
            text("Forged armor comes in three weights, made in the usual armor shapes:"
                 "$(li)$(bold)Brigandine$() (light): hardened leather and rivets on a gambeson. Dyeable. The least armor, no penalty."
                 "$(li)$(bold)Hauberk$() (medium): chain links on a leather backing."
                 "$(li)$(bold)Plate$() (heavy): plates on a leather backing. The most armor and toughness, but a little slow.",
                 "Three Weights"),
            crafting("plate_helmet", recipe2="plate_chestplate"),
            crafting("hauberk_helmet", recipe2="brigandine_helmet"),
        ]),
        "stars": ("Stars on Gear", f"{MOD}:plate_chestplate", [
            text("A piece takes the worst star among its parts, and its stats come from its stars. A ★1 plate set is a little better "
                 "than iron; ★3 is a little better than diamond; ★5 a little better than netherite.", "Stars on Gear"),
            text("With Apotheosis, stars go further: ★4 gear has a gem socket and ★5 two; ★6 and ★7 strengthen the attribute bonuses "
                 "Apotheosis puts on them; and ★7 gear comes off the table already enchanted with an Apotheosis rarity.", "Apotheosis"),
        ]),
        "weapons": ("Weapons", f"{MOD}:forged_sword", [
            crafting("forged_sword", "Two plates over a stick.", "forged_axe"),
        ]),
        "upkeep": ("Repair and Salvage", "minecraft:grindstone", [
            text("Repair forged gear at a vanilla anvil with a tempered ingot of the same star or better."
                 "$(br2)To salvage it, sneak and right-click a forge's grindstone with it. You get back tempered ingots of its star "
                 "(some of its metal, more as your Forging improves, less if it's worn) and nuggets for the rest.", "Repair and Salvage"),
        ]),
    },
    "skills": {
        "skills": ("Crafting Skills", "minecraft:experience_bottle", [
            text("Forging, Leatherworking and Weaving each level from 1 to 30 as you practice. Use $(item)/skills$() to see where "
                 "you stand.$(br2)Higher levels widen sweet spots, lower the odds of ruining a piece, and (for smiths) slow cooling "
                 "and save more scrap from cracks.", "Crafting Skills"),
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
