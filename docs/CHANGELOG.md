# Changelog

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
- Fix: a crash when a hired maiden started working (her work tasks were made before her job existed).

## 0.2.0
- Code reorganized into systems (see DESIGN.md, "Code map"); no behaviour change except:
- Trade prices now drop with affection (up to 30% off at 100); husband still pays half.
- All tunable numbers are in `config/maidens-common.toml`.

## 0.1.0
- First test: maiden houses, maidens (six skins, four personalities), farmer trades, chat/joke/compliment/flirt,
  gifts, marriage by personality, hiring for farming, guarding, woodcutting, mining and fishing.
