package net.trueog.questsOG

import java.io.File
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

/** Raw Mojang texture value + signature, applied to an NPC as-is. */
data class NpcSkinData(val value: String, val signature: String)

/** Where one home-tier NPC stands. World is resolved lazily so config can parse before worlds load. */
data class NpcSpawn(
    val tier: Int,
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val skin: NpcSkinData?,
) {
    fun toLocation(): Location? = Bukkit.getWorld(world)?.let { Location(it, x, y, z, yaw, pitch) }
}

class Config private constructor() {
    lateinit var redisUrl: String
    var debug: Boolean = false
    /** Placed NPCs (entries with a world). */
    var npcSpawns: Map<Int, NpcSpawn> = emptyMap()

    /** Skins per tier, including tiers that have a skin configured but no position yet. */
    var npcSkins: Map<Int, NpcSkinData> = emptyMap()

    companion object {
        const val MIN_TIER = 1
        const val MAX_TIER = 6
        private const val NPCS_KEY = "npcs"

        private fun configFile() = File(QuestsOG.plugin.dataFolder, "config.yml")

        fun create(): Config? {
            val config = Config()
            val file = configFile()
            if (!file.exists()) {
                QuestsOG.plugin.saveDefaultConfig()
            }
            val yamlConfig = YamlConfiguration.loadConfiguration(file)

            try {
                config.redisUrl = yamlConfig.get("redisUrl") as String
            } catch (_: Exception) {
                QuestsOG.plugin.logger.severe("Failed to parse config option \"redisUrl\" as a string")
                return null
            }

            config.debug = yamlConfig.getBoolean("debug", false)
            config.applyNpcs(yamlConfig)

            return config
        }

        /** Re-reads only the npcs section from disk and updates the live config. */
        fun reloadNpcSpawns(): Map<Int, NpcSpawn> {
            QuestsOG.config.applyNpcs(YamlConfiguration.loadConfiguration(configFile()))
            return QuestsOG.config.npcSpawns
        }

        /** Writes a tier's position to config.yml, keeping any skin already configured for it. */
        fun writeNpcSpawn(spawn: NpcSpawn) {
            val file = configFile()
            val yaml = YamlConfiguration.loadConfiguration(file)
            val path = "$NPCS_KEY.${spawn.tier}"
            yaml.set("$path.world", spawn.world)
            yaml.set("$path.x", spawn.x)
            yaml.set("$path.y", spawn.y)
            yaml.set("$path.z", spawn.z)
            yaml.set("$path.yaw", spawn.yaw.toDouble())
            yaml.set("$path.pitch", spawn.pitch.toDouble())
            if (!yaml.isConfigurationSection("$path.skin")) {
                yaml.set("$path.skin.value", "")
                yaml.set("$path.skin.signature", "")
            }
            yaml.save(file)
            QuestsOG.config.applyNpcs(yaml)
        }

        /** Removes only the tier's position; a configured skin is kept for the next /questnpc set. */
        fun deleteNpcSpawn(tier: Int) {
            val file = configFile()
            val yaml = YamlConfiguration.loadConfiguration(file)
            val path = "$NPCS_KEY.$tier"
            if (yaml.isConfigurationSection("$path.skin")) {
                listOf("world", "x", "y", "z", "yaw", "pitch").forEach { yaml.set("$path.$it", null) }
            } else {
                yaml.set(path, null)
            }
            if (yaml.getConfigurationSection(NPCS_KEY)?.getKeys(false).isNullOrEmpty()) {
                yaml.createSection(NPCS_KEY) // Keep `npcs: {}` rather than dropping the key.
            }
            yaml.save(file)
            QuestsOG.config.applyNpcs(yaml)
        }

        private fun Config.applyNpcs(yaml: YamlConfiguration) {
            val (spawns, skins) = parseNpcs(yaml)
            npcSpawns = spawns
            npcSkins = skins
        }

        /** An entry with only a skin is legal: it is an unplaced NPC waiting for /questnpc set. */
        private fun parseNpcs(yaml: YamlConfiguration): Pair<Map<Int, NpcSpawn>, Map<Int, NpcSkinData>> {
            val spawns = sortedMapOf<Int, NpcSpawn>()
            val skins = sortedMapOf<Int, NpcSkinData>()
            val section = yaml.getConfigurationSection(NPCS_KEY) ?: return spawns to skins
            for (key in section.getKeys(false)) {
                val tier = key.toIntOrNull()
                if (tier == null || tier < MIN_TIER || tier > MAX_TIER) {
                    QuestsOG.plugin.logger.warning("Ignoring npcs.$key: tier must be $MIN_TIER-$MAX_TIER")
                    continue
                }
                val entry = section.getConfigurationSection(key)
                if (entry == null) {
                    QuestsOG.plugin.logger.warning("Ignoring npcs.$key: not a section")
                    continue
                }
                val skin = parseSkin(entry.getConfigurationSection("skin"))
                if (skin != null) skins[tier] = skin
                val world = entry.getString("world")
                if (world.isNullOrBlank()) {
                    if (skin == null) QuestsOG.plugin.logger.warning("Ignoring npcs.$key: no world and no skin")
                    continue
                }
                spawns[tier] =
                    NpcSpawn(
                        tier,
                        world,
                        entry.getDouble("x"),
                        entry.getDouble("y"),
                        entry.getDouble("z"),
                        entry.getDouble("yaw").toFloat(),
                        entry.getDouble("pitch").toFloat(),
                        skin,
                    )
            }
            return spawns to skins
        }

        private fun parseSkin(section: ConfigurationSection?): NpcSkinData? {
            val value = section?.getString("value")?.takeIf { it.isNotBlank() } ?: return null
            val signature = section.getString("signature")?.takeIf { it.isNotBlank() } ?: return null
            return NpcSkinData(value, signature)
        }
    }
}
