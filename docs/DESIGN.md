# Maidens: design

The single source of truth for what the mod does and why. Every feature has its rules here before it's built;
every decision made along the way is recorded here. Numbers live in the config file (`config/maidens-common.toml`),
not in code.

## Notes for whoever works on this

- Goals are only checked every other tick, offset per entity, so never gate a goal's `canUse` on
  `tickCount % n` (half the mobs would never pass); use a countdown in the goal instead.
- Mob goals are created in the `Mob` constructor, before the maiden's own fields exist: goals must look her
  parts up when they run (`m.employment()`), never keep them from their constructor.

## Performance rules

- Never look at unloaded land: block scans, POI searches and bed/workstation checks first make sure the chunks
  are loaded (`level.hasChunksAt`), since touching unloaded land loads it (and its POI data) from disk.
- Anything that scans (work targets, chests, monsters to flee, chores) runs on a timer, not every tick, and waits
  longer when the last scan found nothing (`WorkTask.IDLE_SCAN`).
- Lookups go through indexes: the blueprint keeps its pieces per chunk column; building bed counts and the village
  bell are worked out once.
- Maidens only pick up items while working (vanilla looks for items every tick for mobs that can).
- Equipment is only re-set when the item changes (a re-set is sent to every nearby player).
- Test-only bookkeeping is skipped unless the self test runs (`SelfTestFlag`).

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
| `core` | registries (`ModEntities`, `ModItems`, `ModBlocks`) and `MaidensConfig` (every tunable number) |
| `block` | the marked chest (`MarkedChestBlock`, `MarkedChestBlockEntity`) |
| `maiden` | the `Maiden` entity (looks, name, personality, home, saving) and `MaidenMenu` (her right-click menu, server side) |
| `maiden.social` | `Relationships` (affection per player, talking, gifts, proposals), `Bond`, `Dialogue` (reads the line files in `data/maidens/dialogue/`), `Marriages`, `Wishes` (her request) |
| `maiden.work` | `Employment` (hiring, shift, bag, what she wears), `WorkGoal` + one `WorkTask` per job (`FarmTask`, `WoodTask`, `MineTask`, `FishTask` + its `Bobber`), `Delivery` + `DeliverGoal` (to the marked chest) |
| `maiden.trade` | `MaidenTrading` (trades by profession, levels, prices) |
| `maiden.home` | `Residence` (her village, claimed bed and workstation, profession), `Schedule` (her day) |
| `maiden.ai` | `MaidenAi` (every goal, in priority order) and the goals themselves |
| `village` | `Villages` (saved list of maiden villages, the per-village coin flip), `VillageRecord` (name, area, citizens, trades), `Blueprint` (how the village looked), `VillageConversion` (villager to maiden swap), `VillageSocial` (gossip, anger, citizenship, greetings), `VillageEvents` (block break/place, bell, the village tick), `Upkeep` + `Chore` (repairs, road clearing, building work), `Buildings` (vanilla buildings by style, sites, roads), `Housing` (houses for the bedless, newcomers), `VillageMenu` (the bell/`/village` chat menu) |
| `village` (also) | `Royals` (who leads each village), `VillageView` (the bell screen's data and what's done on it) |
| `politics` | `Network` (saved: every village found from the seed, stances, news, raid timers, roads), `KnownVillage`, `Stance`, `Diplomacy` (who may decide, changing a stance), `WarParties` (raids by hostile villages), `Road` + `Roads` (roads maidens lay between villages), `WorldMap` + `BiomeColours` (the seed-map picture) |
| `maiden.war` | `Raider` (a maiden on a raid), `RaidGoals` (advance and steal, flee, targets, defend) |
| `network` | `MenuPayload` (server to client), `ActionPayload` (client to server), `VillageViewPayload` (village screen, a building's blocks, the world map), `VillageActionPayload` (what's done on those screens), `Network` |
| `client` | model, armor model, crown, renderers (maiden, bobber, marked chest), menu screen, client test |
| `client.village` | `VillageScreen` (bell screen), `WorldMapScreen`, `TemplateCache` + `IsoRenderer` (building pictures), `Ui` |
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
- Hiring: `rentEmeralds` for a `shiftTicks` shift (husband: free, no end). She works around where she was
  hired (24 blocks for farming, woodcutting and fishing). Jobs:
  - farm: harvests ripe crops and replants; plants every bare farmland (seeds from her bag, else her own wheat seeds);
  - guard: follows and fights; weapon from her bag;
  - woodcutter: fells whole trees (every log, however tall, then the leaves), replants the same tree (a sapling from
    her bag, else one of her own), then the next tree;
  - miner: staircase down to about y 16, then a tunnel up to 64 long; a torch every 6 steps (from her bag, or 4 made
    from a coal in her bag, or her own); digs out ore spotted within 4 blocks, whole veins; climbs back up her
    staircase when her bag is full or the job ends;
  - fisher: walks to the shore, casts (a bobber on a line, see `Bobber`), waits for a bite, reels in fishing loot.
  What she gathers goes in her bag (employer, last employer or husband can open it).
- Marked chest (chest + gold ingot): when her bag fills, and when the job ends, she carries what she gathered to
  the closest marked chest within 32 blocks of where she was hired, then goes back to work. She keeps her tools,
  weapons, armor, torches, saplings, and a stack each of seeds and filler blocks. With no marked chest, a full bag
  ends the job as before. A marked chest never joins into a double chest.
- Armor: she wears the best helmet and chestplate in her bag (copies; the bag keeps the originals), and holds a
  shield from it while she fights. The armor pieces are vanilla's (same sizes and textures), placed over her parts.
- Trades: farmer trades (or her workstation's profession in a village), levels like a villager, daily restock.
  **Prices follow affection**: up to `maxAffectionDiscount` off at 100; husband pays `marriedPriceFactor`.
- Model: vanilla humanoid animation with a smaller walk swing (legs 24°, arms 17°, 1.25x steps) and limited
  look-up (20°). Movement speed 0.42. Her name shows only in her menu, never above her head.
- Dialogue: 15 lines for each good reaction, 10 for each bad one, gift reactions and hints, per personality,
  in `data/maidens/dialogue/<personality>.json` (plus `common.json`); `%s` is the player's name.

## AI priorities (`MaidenAi`)

1. Trading, talking (her menu is open): stand and look at the player.
2. Fleeing monsters (not when she's a fighter) / fighting (when she's a fighter).
3. Following her employer (hired guard); taking her bag to the marked chest.
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
- Reaching: within 3.5 blocks she works normally; if she can't get closer for 3 s but is within 8, she reaches
  over; if she's stuck further away for 10 s (cliffs, rivers, paths too long to plan) she's put on a free spot
  next to it. A maiden standing in the gap steps aside. Unreachable chores are retried after a minute.
- Where a road's "keep clear" overlaps a block another piece wants, the block wins.

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

## Politics (0.5.0)

Rules:
1. **Royals.** Each maiden village has `Royals.PER_VILLAGE` royals (1 for now; a number, not hard-coded): a free
   resident is crowned when there's none. Royals wear a crown, can't be hired, skip chores, can be married.
2. **Who may decide** a village's stances (`Diplomacy.rightToDecide`): someone married to its royal, someone who
   defended it in a raid (Hero of the Village there, or beat back a war party), or a citizen of 14+ days.
3. **The network** (`politics.Network`): every village is found from the world seed (structure placement, no
   chunk loading), a few spots per tick, around maiden villages and wherever the world map is opened. All are
   shown, visited or not.
4. **Stances** are per neighbour (villages within 1500 blocks): trading, neutral or aggressive. Each village gets
   a temperament at random (seeded) that decides its starting stances. Its royal may turn hostile towards a
   village whose raid was beaten back. Players with the right change their village's side.
5. **Raids** (`WarParties`): a village aggressive towards a neighbour sends a war party every 3-5 days, only while
   a player is within 128 blocks of the target. 3-5 armed maidens in their village's colour appear at the edge
   facing the attacker, walk to the bell, break doors, steal from chests (2 stacks each, up to 4). Beaten ones
   (30% health) surrender, drop their swords and run home. Residents are never killed by raiders (kept at 2 hp).
6. **Roads** (`Roads`): to each village's 2 nearest non-hostile neighbours. Maidens lay 48 steps a day from each
   end, only in loaded land: a 3-wide path on the ground, plank bridges over water, a lantern post every 32.
7. **Bell screen** (`VillageView` + `VillageScreen`): a map of the village (buildings outlined; hover shows who
   sleeps there; beds marked with names); tabs Build (a card per building with a turning isometric picture; pick
   one, click the map, right-click/R turns it), Diplomacy (each neighbour, both stances, buttons for those with
   the right), News. `/village chat` keeps the old chat menu.
8. **World map** (`WorldMap` + `WorldMapScreen`): 2048 x 2048 blocks of biome colours from the seed (worked out
   off the main thread, cached), every village with its name and stance towards you, roads (built solid,
   planned faint). Drag to move, scroll to zoom; click a village for details and stance buttons.

## Phases

0. Reorganize into systems, design doc, config. (done in 0.2.0)
1. Maiden villages: replacement, workstations and beds (vanilla POIs), profession trades. (done in 0.3.0)
2. Village records: name and title, blueprint, anger, gossip, citizenship and advancement. (done in 0.3.0)
3. Repairs and road clearing. (done in 0.3.0)
4. Housing: new houses, roads to them, the bell menu, new maidens arriving. (done in 0.3.0)
5. Daily schedule, requests board, raid defence. (done in 0.3.0)
6. Politics: royals, the village network, stances, raids, roads, bell screen, world map. (done in 0.5.0)
