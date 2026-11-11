# Changelog

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
