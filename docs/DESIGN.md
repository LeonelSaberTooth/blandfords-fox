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
| `maiden.social` | `Relationships` (affection per player, talking, gifts, proposals), `Bond`, `Dialogue`, `Marriages` |
| `maiden.work` | `Employment` (hiring, shift, bag), `WorkGoal` + one `WorkTask` per job (`FarmTask`, `WoodTask`, `MineTask`, `FishTask`) |
| `maiden.trade` | `MaidenTrading` (trades by profession, levels, prices) |
| `maiden.home` | `Residence` (her village, claimed bed and workstation, profession) |
| `maiden.ai` | `MaidenAi` (every goal, in priority order) and the goals themselves |
| `village` | `Villages` (saved list of maiden villages, the per-village coin flip), `VillageRecord`, `VillageConversion` (villager to maiden swap) |
| `network` | `MenuPayload` (server to client), `ActionPayload` (client to server), `Network` |
| `client` | model, renderer, menu screen, client test |
| `command` | `/maiden` test commands |
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
2. Village records: name and title, blueprint, anger, gossip, citizenship and advancement.
3. Repairs and road clearing.
4. Housing: new houses, roads to them, the bell screen, new maidens arriving.
5. Daily schedule, requests board, raid defence.
