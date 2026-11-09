# Maidens

A NeoForge 1.21.1 mod (no other mods needed). A first test of an RPG dating / adventure / life simulator,
in the spirit of Minecraft Comes Alive, Villager Recruits and Millénaire.

## Install

Download the `.jar` from the **Releases** section (the "latest" release) and put it in your `mods` folder.

## What's in it

- **Maiden houses**: small oak cabins with a wheat plot, found now and then in plains, meadows and forests,
  each with a maiden living in it. `/locate structure maidens:maiden_house` finds one; there's also a spawn egg.
- **Maidens**: six looks, a random name and one of four personalities (Sweet, Hardworking, Free-spirited, Adventurous).
  Right-click one to open her menu.
- **Trade**: farmer trades, levelling up from Novice to Master like a villager.
- **Chat, Joke, Compliment, Flirt**: each can go well or badly depending on her personality and how much she likes you.
  Doing the same thing over and over in a day stops working.
- **Gifts**: hold something and press Gift. Each personality loves, likes and dislikes different things.
- **Hire her** for a Minecraft day (5 emeralds) to farm, guard you, cut wood, mine or fish. What she gathers goes in her bag.
- **Marriage**: at full hearts, give her the gift she dreams of (a cake, a diamond pickaxe, a goat horn or a saddle,
  depending on her personality; she drops hints once she's fond of you). Married, she works for free for as long as you like
  and trades at half price. One spouse per player.

- **Maiden villages**: some villages are maiden villages, with a crowned royal. They repair themselves, grow,
  and get along (or not) with their neighbours: trade, neutrality, or war parties. Click the bell for the village
  screen (map, building, diplomacy, news) and the world map.

Testing commands (operators): `/maiden info` and `/maiden affection <0-100>` on the nearest maiden.

## Editing the model

The Blockbench file is `art/maiden.bbmodel`. After changing it, run `python3 tools/convert_model.py`
to regenerate the model code and the skins. The house is built by `tools/make_house.py`.
