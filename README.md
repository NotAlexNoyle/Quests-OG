# Quests-OG
Quests plugin for TrueOG

## Quest NPCs
Six player NPCs, one per home tier, powered by a shaded [NpcApi](https://github.com/Eisi05/NpcApi-Spigot) 2.3.3
(the last release for Java 17 / 1.19.4). Tier 1 is a greeter that explains the ladder in chat; tiers 2-6 open the
quest menu directly on their quest, with a **Claim** button on the player's current quest. Nametags are per player:
green ✔ for completed tiers, yellow for the current one, gray ✖ for locked ones.

NPCs are never persisted by NpcApi; they are rebuilt from `config.yml` on every enable.

### `/questnpc` (permission `questsog.admin`, default op)
- `/questnpc set <tier>` — place (or move) the NPC for that tier at your location. Writes `npcs.<tier>` to config.yml.
- `/questnpc remove <tier>` — delete the NPC and its config entry.
- `/questnpc tp <tier>` — teleport to the NPC.
- `/questnpc list` — show configured and spawned tiers.
- `/questnpc reload` — re-read the `npcs` section and respawn.

### Skins
The bundled config ships a MineSkin texture per tier (sage, adventurer, miner, sorcerer, ranger, dark knight), so a
fresh install only needs `/questnpc set <tier>` for each. Each `npcs.<tier>.skin` takes a raw texture `value` and
`signature` (for example from [mineskin.org](https://mineskin.org)); an entry with only a skin is an NPC that is not
placed yet. Leave both blank for the default skin. An invalid signature results in the default skin.

## Permissions used
- questsog.admin
- essentials.sethome.multiple.homes-2
- essentials.sethome.multiple.homes-3
- essentials.sethome.multiple.homes-4
- essentials.sethome.multiple.homes-5
- essentials.sethome.multiple.homes-6

## Requirements for Each Home
### One home
Free
### Two homes
- 100 diamonds
- 24 hours played
- 10k blocks travelled
- 50 levels
- 10 Duels wins
### Three homes
- 250 diamonds
- 5 days played
- 50k blocks travelled
- Beaconator advancement
- 100 levels
- 20 Duels wins
### Four homes
- 1000 diamonds
- 10 days played
- 200k blocks travelled
- A Furious Cocktail advancement
- Serious Dedication advancement
- 150 levels
- Have died to death.fell.accident.water
- 50 Duels wins
### Five homes
- 2500 diamonds
- 15 days played
- 5000 blocks travelled on pig
- 1000 blocks travelled on strider
- Kill 50 dolphins
- Kill 50 zoglins
- The Cutest Predator advancement
- Two by Two advancement
- A Complete Catalogue advancement
- Monsters Hunted advancement
- Have died to fell while climbing
- Have died to magma block while fighting zoglin
- 200 levels
- 2000 fish caught
- Villager head
- 150 Duels wins
### Six Homes
- 5000 diamonds
- 30 days played
- 10000 blocks walked on water
- 10000 blocks walked underwater
- All 13 music discs
- All advancements
- 1500 blocks of obsidian mined
- 5 dragon eggs
- 250 levels
- 300 Duels wins
