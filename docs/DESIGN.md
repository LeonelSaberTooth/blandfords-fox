# Maidens: design

The single source of truth for what the mod does and why. Every feature has its rules here before it's built;
every decision made along the way is recorded here. Numbers live in the config file (`config/maidens-common.toml`),
not in code.

## Principles

- **One system per package, one entry point per system.** A system talks to others through small, named methods,
  never by reaching into their fields.
- **Simple first.** The simplest rules that give the feature; anything clever waits until it's needed.
- **No mixins, no changes to world generation.** Only NeoForge events and vanilla systems (POIs for beds and
  workstations, structure data, saved data), so other mods keep working.
- **Performance.** Nothing scans the world every tick; village work runs on a small per-tick budget; data is saved
  once per world, not per entity per tick.
- **Tested.** Every feature gets a step in the automatic game test (`test/SelfTest`), and the client test takes
  screenshots of anything visual. Old steps keep running.

## Code map

| Package | What it is |
|---|---|
| `MaidensMod` | start-up: registers each system |
| `core` | registries (`ModEntities`, `ModItems`) and `MaidensConfig` (every tunable number) |
| `maiden` | the `Maiden` entity (looks, name, personality, home, saving) and `MaidenMenu` (her right-click menu, server side) |
| `maiden.social` | `Relationships` (affection per player, talking, gifts, proposals), `Bond`, `Dialogue`, `Marriages`, `Wishes` (her request) |
| `maiden.work` | `Employment` (hiring, shift, bag), `WorkGoal` + one `WorkTask` per job (`FarmTask`, `WoodTask`, `MineTask`, `FishTask`) |
| `maiden.trade` | `MaidenTrading` (trades by profession, levels, prices) |
| `maiden.home` | `Residence` (her village, claimed bed and workstation, profession), `Schedule` (her day) |
| `maiden.ai` | `MaidenAi` (every goal, in priority order) and the goals themselves |
| `village` | `Villages` (saved list of maiden villages, the per-village coin flip), `VillageRecord` (name, area, citizens, trades), `Blueprint` (how the village looked), `VillageConversion` (villager to maiden swap), `VillageSocial` (gossip, anger, citizenship, greetings), `VillageEvents` (block break/place, bell, the village tick), `Upkeep` + `Chore` (repairs, road clearing, building work), `Buildings` (vanilla buildings by style, sites, roads), `Housing` (houses for the bedless, newcomers), `VillageMenu` (the bell/`/village` chat menu) |
| `network` | `MenuPayload` (server to client), `ActionPayload` (client to server), `Network` |
| `client` | model, renderer, menu screen, client test |
| `command` | `/maiden` test commands (operators), `/village` (everyone) |
| `test` | the automatic game test |

## Maidens (done)

- Six skins, a random name, one of four personalities. Personality decides gift tastes, how each kind of chat
  goes down, and the proposal gift: Sweet (cake), Hardworking (diamond pickaxe), Free-spirited (goat horn),
  Adventurous (saddle).
- Affection 0-100 per player. Chat, joke, compliment, flirt: chance of success from personality and affection;
  each kind works `talksPerDay` times a day. Gifts: loved +10, liked +5, neutral +1 (+3 for valuables),
  disliked -5, diminishing through the day, `giftsPerDay` a day, the same item once a day. Trading: +1 per trade,
  `tradeHeartsPerDay` a day. Above 70 she sometimes hints at her proposal gift.
- Proposal: her personality's gift at 100 affection. One spouse per player (`Marriages`, world-wide).
- Hiring: `rentEmeralds` for a `shiftTicks` shift (husband: free, no end). Jobs: farm (around her house),
  guard (follows and fights; weapon from her bag), woodcutter, miner (staircase then tunnel), fisher.
  What she gathers goes in her bag (employer, last employer or husband can open it).
- Trades: farmer trades (or her workstation's profession in a village), levels like a villager, daily restock.
  **Prices follow affection**: up to `maxAffectionDiscount` off at 100; husband pays `marriedPriceFactor`.
- Model: vanilla humanoid animation with a smaller walk swing (legs 14°, arms 10°) and limited look-up (20°).

## AI priorities (`MaidenAi`)

1. Trading, talking (her menu is open): stand and look at the player.
2. Fleeing monsters (not when she's a fighter) / fighting (when she's a fighter).
3. Following her employer (hired guard).
4. Hired work.
5. Going back inside her area (home, work spot).
6+. Doors, wandering (only when not hired), looking around.

Village duties (Phase 1+) slot in below hired work and above wandering: **anything a player asked of her
(hire, marriage) overrides village life.**

## Maiden villages

How it works (Phase 1):
- `VillageConversion`: a villager joining the world for the first time with vanilla's "pick a profession on
  first tick" flag (only villagers placed by a village structure have it) is checked on the next tick; if the
  village it stands in rolls maidens, it's removed (babies) or swapped for a maiden. Nothing about world
  generation changes.
- `Residence` (village maidens only): every 5 s claims a free bed and a free workstation within 48 blocks,
  through vanilla's POI tickets, so maidens and villagers never share one. The workstation sets her profession
  and trades; once she has traded, the profession stays (she looks for another workstation of that kind).
  Her bed becomes her home. Dying frees both.
- The menu shows her profession next to her personality.

Phase 2:
- Each village gets a name from its id (Ashford, Willowmere...). Strangers are welcomed to it as "traveller",
  citizens are greeted as "neighbour".
- `Blueprint`: each structure piece (house, road...) is recorded block by block once all its chunks are loaded.
  Natural blocks are left out (block tag `maidens:not_village_blueprint`: terrain, plants, leaves, crops), and so
  are air and fluids.
- Breaking a blueprint block (non-citizen) costs `angerPerBlock` with every loaded resident, capped at `angerPerDay`
  per maiden per day; the nearest resident scolds you (at most every 30 s). Explosions don't anger (repairs, Phase 3).
- Gossip: when a player changes one maiden's affection by their own action, each other resident has
  `gossipSharePercent` chance to hear the same change. Hearsay and anger don't spread further.
- Citizenship (any of): residents' average affection >= `citizenAverageAffection`, `citizenTrades` trades in the
  village, or marrying a resident. Gives the "One of Us" advancement. A citizen's breaking and placing updates the
  blueprint, so maidens won't undo it.
- `/maiden village` shows the village you stand in; `/maiden village citizen` makes you one (testing).

Phase 3 (`Upkeep`, `Chore`, `VillageChoreGoal`):
- Every 5 s a slice of each loaded village's blueprint is checked (8192 spots). A blueprint block that's gone
  (air, a plant, fluid or fire in its place) becomes a REPAIR chore; something solid standing on a road
  (block tag `maidens:village_road`, just dirt paths for now) becomes a CLEAR chore. A different solid block
  where a blueprint block was isn't touched (someone put it there on purpose).
- Chores wait `repairDelay` ticks (30 s) before a maiden takes them, so a player still busy there isn't undone.
- A free village maiden (not hired, not married, not asleep) takes the lowest, then nearest, chore within
  80 blocks, carries the block (or a shovel) there and does it. The two halves of a door or bed go back together.
  Repairs cost nothing; cleared road obstacles drop as items. Chores aren't saved (the scan finds them again).

Phase 4 (`Buildings`, `Housing`, `VillageMenu`, `/village`):
- A village's buildings are read from its vanilla "houses" pool (`village/<style>/houses`), so they're exactly
  the vanilla ones for that style (and any a datapack adds there).
- A building is planned at a flat (slope <= 3), dry, free spot near the village (not touching other pieces),
  with a foundation filled in under it, and a road from its door (the template's jigsaw) to the nearest village
  road over walkable ground. Both are added to the blueprint as pieces "under construction", which also mark the
  spots to clear (the air inside the house, terrain in the way). The maidens then build them through ordinary
  chores, from the ground up. When nothing is left to do, the pieces become ordinary blueprint pieces.
- Once a minute per loaded village: a resident who's had no bed for a minute gets a house with a bed (one
  building at a time); otherwise a free bed brings a new maiden (at most one a day, at most 40 residents), who
  appears on a road near it.
- Citizens choose buildings by ringing the village bell or typing `/village`: a chat menu of the style's
  buildings; clicking one runs `/village build <name>`, built near where they stand. Anyone gets the village's
  name, their standing, the requests board and what's being built.

Phase 5:
- Schedule (`Schedule`, `ScheduleGoal`; free village maidens only, hired or married ones follow the player):
  0-2000 morning around home, 2000-9000 at her workstation, 9000-11000 at the village bell, 11000-12000 home,
  then asleep in her bed until morning. Talking to her or hurting her wakes her. No chores at night.
- Requests board (`Wishes`): now and then (a 1 in 4 chance each minute when she has none) a village maiden wants
  4-16 of something for her work (by profession), for 1-4 emeralds and +6 affection; it lasts three days. She
  mentions it when greeting you; Gift while holding enough of it hands it over. The village menu lists them all.
- Raid defence: while a raid is on her village, free village maidens (and hired guards) are fighters: they
  take an iron sword if their hands are empty and attack raiders and monsters instead of fleeing; afterwards
  they put the sword away.

Decisions (user, 2026-11-08: all defaults accepted):

1. All five vanilla village styles; abandoned (zombie) villages stay as they are.
2. Each new village has `maidenVillageChance` (50%) of being a maiden village, fixed per village from the world
   seed. Villagers spawned by the village's own structure are replaced by maidens in the same spot. Where and how
   villages generate is never touched.
3. Baby villagers are left out. Nitwits and unemployed villagers become maidens without a workstation.
4. Iron golems and cats stay.
5. Growth: when the village has a free bed, a new maiden walks in along a road, at most one per day.
6. New houses (for maidens without a bed) only in maiden villages.
7. Repairs and new buildings are built gradually, a block at a time, by a maiden.
8. Repairs and building are free (no materials) for now.
9. A citizen's changes update the village blueprint (maidens don't undo them).
10. Road clearing: blocks on top of road blocks, and holes in them; removed blocks drop beside the road.
11. Anger: each broken blueprint block costs `angerPerBlock` affection with every maiden of the village,
    capped at `angerPerDay` per maiden per day. Iron golems react as in vanilla.
12. Citizenship, any of: average affection of the village's maidens >= `citizenAverageAffection`;
    `citizenTrades` trades in that village; married to a resident. Gives an advancement; remembered per village.
13. Citizens choose buildings at the village bell (village screen), from that village's style.
14. Maidens without a workstation keep farmer trades.
15. Standalone maiden houses stay.

Ideas accepted: village name and greeting title; daily schedule (work, evening at the bell, sleep; hired work
and marriage override it); gossip (`gossipSharePercent` of a change spreads to the village); requests board;
prices follow affection (done); maidens defend in raids.

## Phases

0. Reorganize into systems, design doc, config. (done in 0.2.0)
1. Maiden villages: replacement, workstations and beds (vanilla POIs), profession trades. (done in 0.3.0)
2. Village records: name and title, blueprint, anger, gossip, citizenship and advancement. (done in 0.3.0)
3. Repairs and road clearing. (done in 0.3.0)
4. Housing: new houses, roads to them, the bell menu, new maidens arriving. (done in 0.3.0)
5. Daily schedule, requests board, raid defence. (done in 0.3.0)
