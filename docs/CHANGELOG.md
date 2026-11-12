# Changelog

## 0.12.0
- Trees taken out (inside villages, on fields, where something's built) leave nothing behind: their leaves go as
  they would decay, with the snow on them, the vines and cocoa on them and the bee nests; nothing drops. Trees whose
  tops are under snow are found too, and trees growing inside a village's own pieces count as trees.
- Structures go in without seeds and flowers dropping: plants that lose their footing just go. Glass panes, bars,
  fences, walls and stairs join up with what's round them as they're placed (the castle's windows).
- Two or three fields per village, laid out sooner (once the land by the gates is loaded), each kept clear of the
  others; their edge by the walls winds instead of running straight; the path to a field crosses water on a bridge.
- A road from the castle's gate to the village's roads (tried again until it's joined).
- Roads between villages are off for now.
- Building anywhere there's room: only another structure is in the way. Trees there are felled, a foundation goes
  under it (through shallow water too), and higher ground is dug away. A building whose door can't be joined to a
  road still goes up.
- Villages are lived in: every free bed gets someone (a married couple in a house with two free beds), and after
  that a newcomer arrives every couple of minutes while a bed is free.
- Married couples move in together (a free bed in one's house, or a swap with someone single).
- Children aren't crowned. The royal and their husband or wife sleep in the castle (others move out of its beds) and
  spend the day in the throne room.
- Maidens asleep aren't pushed off their beds, and get up if they're moved off one.
- Casual clothes only at home time, near her bed, under a roof.
- A sword left in a maiden's hand after a raid is put away (not a guard's).
- Structure villagers in maiden villages never come into the world (no more villagers you can see but not touch).
- No iron golems in maiden villages (one a player builds is fine).
- Raiders make for the nearest gate, go through it, and get out of places they're stuck.
- Names are no longer written on the village map (they're in each building's tooltip).

## 0.11.0
- Fields made anew. A village's field is the land itself turned to wheat: a stretch of open ground outside the walls,
  by a gate, following the lie of the land (no levelling), with a natural ragged outline that stops at water, cliffs,
  rock, the walls and the village's buildings. Trees on it are taken out. Waterways wind across it about every 12
  blocks. A path runs from inside the village, out of the gate, to the field and through it; the farmers work along
  it. All wheat, ripe when it's made, and every crop stays (they're set down without the game re-checking them while
  the light is still being worked out, which knocked half of them off before).
- Farmers bring in 16 wheat a day.
- Maidens don't trample farmland.

## 0.10.1
- Men, old men and boys have the vanilla player's body (wide arms, the skin's outer layer) and move as a player does,
  each in one of the vanilla player skins (Steve, Alex, Ari, Efe, Kai, Makena, Noor, Sunny, Zuri), and wear the
  player's armour. Their job's hat goes on top as before.

## 0.10.0
- A village's day. Grown men and women: at home 6-8 in casual clothes, at work 8-15 in work clothes, at the market
  round the bell 15-19 (a woman in her dress), home again 19-21 in casual clothes, asleep 21-6. Children are home,
  out playing round the village, then home; the old are home, at the market, then home. Neither changes clothes.
- You can only trade with a village resident at the market.
- Every grown man and woman has a job for the village, and goes through the motions of it at a spot of their own
  (nothing is really cut, dug or picked): farming at the edge of the fields (farmer or shepherd), cutting wood at the
  edge of a wood (fletcher or leatherworker), mining at a cliff or cave mouth (mason, weaponsmith, toolsmith or
  armorer), fishing on a shore (fisherman, with a line and float), or guarding: keeping a gate or walking round the
  walls in an iron helmet and chestplate with a sword. Guards come first (one per gate or two, and one more for
  every eight people); the rest are four farmers to two woodcutters, two miners and a fisher, as there's room.
- What a village brings in is a fixed amount a day per worker: a farmer 16 food, a woodcutter 24 logs, a miner 32
  stone with some coal and iron (now and then something rarer), a fisher 12 fish. Guards bring in nothing.
- A few workers at a time leave their work for repairs and building.
- Hiring takes 10 affection. Hired work overrides her day, except that a resident goes home to sleep at night.
- Follow me (in her menu): she walks with you round her village at 20 affection, anywhere at 80 (your wife always).
- Fields are big patchworks of plots of different sizes with paths between them, mostly wheat, fully grown when laid
  out, with ragged edges; a crop picked is put back as a seedling.
- Nine in ten of the trees inside a village (its walls, or among its houses) are taken out.
- Fix: a maiden married to another villager showed as single in her menu (it now says who she's married to). When
  one of a couple dies, the other is free to marry again.

## 0.9.1
- Fix: a "model" name tag renamed the woman "model" instead of giving her the model's looks and walk (the game
  names a mob with a tag before she sees the click). A woman already named "model" that way gets a new name and the
  model's looks when the world loads.

## 0.9.0
- Grown women have the new body (the base model from the model viewer): the angled chest piece, the long hair cube,
  and straight legs under a dress. Their looks come from bigger banks: 5 skin tones, 21 hairs (with the shadow the
  hair casts on the forehead), 8 eye colours and 21 dresses. Some have freckles.
- What they wear: swimwear at home, a dress outside, and at work the dress with only their trade's own things over
  it (apron, gloves, tools; glasses and the like on the eyes) and the trade's hat, which shows over the hair.
- A woman blushes for a few seconds when her hearts for you go up, and the first time she talks to you she
  introduces herself ("Hi, I'm ...!") with a smile.
- A name tag that says "model" gives a woman the proportions saved in the model viewer (taller, fuller chest,
  hourglass waist, wider and deeper hips) and the runway walk. She keeps her name; the tag is used up. Another
  "model" tag puts her back.
- Men, the old and children look as before.

## 0.8.0
- Looks are put together from layers: skin tone, clothes, eye colour and hair are chosen separately for each maiden
  (normal, casual and children's clothes). The model is player height again, with the smaller chest.
- Arms sway half as much when idle.
- Casual clothes indoors are the casual texture, over each maiden's own skin tone.
- Work clothes: at work a maiden wears a vanilla villager's clothes for her trade (its robe for where she is, and
  its trade's apron, gloves and so on), with the trade's hat.
- The village store: farmers bring in their harvest, and villages now give their own people work: woodcutters
  (logs), miners (stone and ore) and fishers (fish), in work hours, delivered to the store by the bell at the end of
  the day. The bell screen has a Store tab.
- Villages eat one meal a day for every two people. Newcomers only move in with food to spare, and when the beds
  are full the village builds a new house from its own logs and stone (24 of each), so well-fed villages grow (up
  to 60 people). What citizens order built is still free.
- Village guards keep their helmets.

## 0.7.0
- People: men, old women and men, and children (who grow up in three days) live in maiden villages (placeholder
  looks for now). Half the grown-ups and most of the old marry each other. Some are guards: one at each gate.
- Villager job hats while working; casual clothes (and the casual body) indoors.
- The model's legs are a pixel longer; the thin-legged body is for casual clothes only.
- Reputation: visitor, citizen, leader (married its royal or saved it from a raid). Leaders can lead raids.
- Every village has its own banner (castle, wall towers, by the bell); desert castles and walls are sandstone.
- Fields of crops outside the village (by the gates), tended by its farmers.
- Your home village (best standing) keeps running while you're away.
- Walls: two gates with guards, planned as soon as the village is found and built as the land loads; raiders come
  in through a gate; the royal sleeps in the castle.
- Fixes: arm swings, faster farming, woodcutters replant and give up unreachable trees, miners find ores (and dig
  for a held ore or stone), fishing in ponds, no suffocating, no teleporting in sight, getting up out of bed,
  home in the morning and evening, walking on powder snow, building near water, 3-wide village paths, roads join
  the streets, jobless villagers in maiden villages replaced, a faster and bigger world map with coordinates.

## 0.6.0
- Castles and walls: about a third of maiden villages have a stone castle (keep with a throne room and the
  royal's bedroom, corner towers, gatehouse) and walls with towers and gates around the whole village.
  Other villages can build them: Castle and Walls cards in the bell's Build tab.
- `/village find walled` (or `maiden`) gives the nearest such village.
- The world map marks walled villages.
- Maidens lay a few blocks at a time on building sites, so buildings go up faster.

## 0.5.0
- Royals: each maiden village has a crowned royal (not for hire) who leads it. Marry her, defend the village in a
  raid, or be a citizen for 14 days to speak for the village yourself.
- Villages know each other (all of them, from the seed) and treat each neighbour as a trading partner, neutral
  or hostile; each starts out differently.
- Hostile villages send war parties every few days while you're near: armed maidens in their village's colour
  who break doors and steal from chests. Beaten ones surrender and run home.
- Maidens build roads (with bridges and lantern posts) to friendly neighbours, a little each day.
- The bell opens a village screen: a map of the village with who sleeps where, a Build tab with turning
  pictures of each building (click the map to choose the spot), Diplomacy and News.
- World map: a seed map of biomes with every village, its name and how it treats you, and the roads.

## 0.4.1
- The marked chest looks exactly like a vanilla chest (block and item), with a gold lock.
- Fix: a crash right after a maiden finished unloading at a marked chest.
- Armor uses the vanilla armor sizes, placed to fit her.
- Performance: no more reading unloaded land from disk (bed and workstation searches, job scans, newcomers);
  job scans wait longer when there's nothing to do; fleeing checks twice a second instead of every other tick;
  maidens only look for items to pick up while working; faster blueprint lookups (per chunk); building bed counts
  and the village bell are cached; guards no longer resend their weapon every second; cheaper villager conversion
  check; test-only bookkeeping skipped in normal play.

## 0.4.0
- Walking: a bigger leg swing with slightly quicker steps, and she moves a bit slower, so her feet match the ground.
- Fixed the flicker where her hips and legs overlap.
- Names no longer float above maidens' heads (they're in the menu).
- Armor: she wears the best helmet and chestplate in her bag, and a shield from it when she fights, shaped to her.
- Five times as many reaction lines; they're now in data files (`data/maidens/dialogue`).
- Marked chest (chest + gold ingot): maidens bring what they gathered to the closest one near where you hired
  them, when their bag fills and when the job ends.
- Farming plants bare farmland too. Woodcutting fells whole trees (tall ones too), clears the leaves, replants,
  and moves on to the next tree. Mining lights the tunnel, digs out ore she spots, and climbs back out. Fishing
  shows a bobber and line, and she no longer gets stuck on water she can't reach.
- Jobs reach 24 blocks around where she was hired (farming used to be around her house).

## 0.3.0
- Maiden villages: each village has a 50% chance (fixed by the world seed) that its villagers are maidens instead.
- Village maidens claim beds and workstations like villagers; the workstation sets their profession and trades,
  locked once they've traded. The menu shows the profession.
- Villages have names; maidens welcome strangers and greet citizens as neighbours.
- Maidens remember how their village looked; breaking its blocks angers every resident (capped per day).
- Gossip: what you do to one maiden can reach the others.
- Citizenship per village (liked by all, 30 trades, or marrying a resident), with the "One of Us" advancement.
  Citizens can change the village freely. Commands: `/maiden village`, `/maiden village citizen`.
- Maidens repair broken or blown-up village blocks (after 30 s) and clear things put on roads.
- Bedless maidens get a new vanilla house of the village's style, built block by block, with a road to it.
  Free beds bring new maidens (one a day). Citizens pick buildings from the bell (or `/village`).
- Free village maidens keep a daily routine (work, evening at the bell, sleep in bed), post requests for items
  (paid in emeralds), and take up swords when their village is raided.
- A maiden stuck far from a chore or her bed finds her own way there; new houses go where the residents live,
  and only where a road can reach them.
- Fix: half the maidens never started village chores or their routine.
- Fix: a crash when a hired maiden started working (her work tasks were made before her job existed).

## 0.2.0
- Code reorganized into systems (see DESIGN.md, "Code map"); no behaviour change except:
- Trade prices now drop with affection (up to 30% off at 100); husband still pays half.
- All tunable numbers are in `config/maidens-common.toml`.

## 0.1.0
- First test: maiden houses, maidens (six skins, four personalities), farmer trades, chat/joke/compliment/flirt,
  gifts, marriage by personality, hiring for farming, guarding, woodcutting, mining and fishing.
