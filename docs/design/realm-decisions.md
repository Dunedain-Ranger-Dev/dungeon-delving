# How the realm's factions decide

Every turn (20 real minutes, `realmTurnMinutes`), the kingdom acts once, then each monster nation acts once. Each side
looks at the map, gives each of its possible actions a **weight**, and picks one at random in proportion to the
weights. A weight of 6 against 2 and 2 means a 60% chance, not a certainty, so sides don't act like clockwork. The
code is `RealmTurns` (`kingdomWeights`, `nationWeights`); the log (`/realm log`) records every choice.

Before anyone acts, two things happen on their own:

- **Repairs:** any town with raid damage still to rebuild pays 1 wealth and rebuilds 64 blocks (`RealmDamage.fundRepairs`).
- **Growth:** any kingdom town with 10 × its tier in wealth, plus 5 to spare, and nothing left to rebuild, grows a tier (`RealmTiers.grow`).

Raids declared on a turn resolve at its "dusk", half a turn later.

## The kingdom

The kingdom looks at its **border towns** (towns linked to a monster stronghold) and the **threat**: the total
military of every monster stronghold touching the border.

| Action | Weight | What it does |
| --- | --- | --- |
| Reinforce | 2, plus 4 if the threat is greater than the border's defense (its towns' defense plus the army). 0 if there's no border | Every border town gains 2 defense |
| Muster | 2, plus 2 if the army is less than half the threat | The army grows by 3 (a quarter of it helps defend any raided town) |
| Gather wealth | 2, plus 2 if the treasury is under 10 | Every town gains 1 wealth; the treasury gains 1 per town |

So a kingdom under pressure mostly reinforces and musters. A safe one spreads its effort evenly, and its towns slowly
get richer and grow.

## Each monster nation

A nation (goblins, undead, orcs) looks at its **strongholds**, their total **military**, and its **raid target**:
the weakest-defended kingdom town linked to any of its strongholds.

| Action | Weight | What it does |
| --- | --- | --- |
| Build military | 3, plus 2 if its military is below the target's defense | Every stronghold gains 2 military |
| Raid | 1, plus 1 for every 3 military it has over the target's defense. 0 with no target, or a raid already under way | Declares a raid on the target, from the strongholds linked to it |
| Fortify | 1, plus 1 if its weakest stronghold's fortification is under 5 | Every stronghold gains 1 fortification |

So a nation weaker than its target mostly builds up. Once it outmatches the target, raiding grows likelier the
bigger its lead: 15 military over the target's defense makes raid weight 6 against build 3 and fortify 1-2.
Fortification currently only counts toward this choice; it will matter once the kingdom can attack strongholds.

## Capitals

Each nation can have a **capital** (a `capital` node). It can never be taken or cleared, and the land around it stays
dangerous: at least danger 6 at its heart, fading out over 360 blocks (`RealmCapitals`).

- While the nation holds any stronghold, its capital just waits.
- Once it has lost them all, it's **pushed back**. Building military now goes to the capital, and each turn the
  capital **rallies**. Every 3 turns of rallying, its **champion** marches on the nearest kingdom town as a raid. The
  champion is one of the nation's dungeon bosses, tier 1 plus 1 per 20 of the capital's military, up to 4. A rolled
  champion raid gets +10 attack; a fought one brings the champion with the last wave.
- Players can call the champion out at the capital with `/challenge` (within 48 blocks of it, once the nation is
  pushed back, one champion at a time).
- **Killing the champion** costs the capital a third of its military and resets its rally. A champion that sacks a
  town, or kills its challenger, adds 5 military.

## Raids

At dusk a raid is fought if a player is within 48 blocks of the town. Otherwise it's rolled:

- attack = the stronghold's military + d20
- defense = the town's defense + a quarter of the kingdom's army + d20

A fought raid uses the damage each side does instead.

| Result | What happens |
| --- | --- |
| Attackers win by a margin | The town loses defense (a quarter of the margin, at least 1), wealth (2 + a quarter of the margin) and 2 morale, and takes block damage equal to the margin. A margin of 10 or more also knocks it down a tier. The stronghold loses 1 military |
| Defenders win | The stronghold loses 2 military, plus a quarter of the margin, and the town gains 1 morale |

## What players change

Players affect these choices only through the numbers the factions read:

- Fighting raids changes their outcome: more defense, morale and wealth for the town, less military for the nation.
- Taking or clearing a stronghold (`/realm node owner`, for now) removes it from its nation. Its land calms, and the
  nation loses that much military and those border links.
- Admins can set any stat (`/realm node set`) to steer a world.

## Not decided yet

- Nations don't yet expand, take kingdom nodes, or work together.
- The kingdom doesn't yet attack strongholds itself; for now that's the players' job.
- Nations don't yet retake strongholds from their capital.
