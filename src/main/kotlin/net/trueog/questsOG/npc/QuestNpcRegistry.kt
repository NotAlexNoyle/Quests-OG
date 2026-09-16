package net.trueog.questsOG.npc

import de.eisi05.npc.api.objects.NPC
import de.eisi05.npc.api.objects.NpcOption
import de.eisi05.npc.api.objects.NpcSkin
import de.eisi05.npc.api.objects.Skin
import net.trueog.questsOG.Config
import net.trueog.questsOG.NpcSpawn
import net.trueog.questsOG.QuestsOG
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player

/**
 * Owns the live quest NPCs. NPCs are ephemeral: built from config on enable, never saved through NpcApi, and removed on
 * disable. All methods must run on the main thread.
 */
class QuestNpcRegistry {
    private val npcs = sortedMapOf<Int, QuestNpc>()

    /** Spawns whose world was not loaded yet; retried from [onWorldLoaded]. */
    private val pendingWorlds = mutableMapOf<Int, NpcSpawn>()

    private val debug
        get() = QuestsOG.config.debug

    fun get(tier: Int): QuestNpc? = npcs[tier]

    fun tiers(): List<Int> = npcs.keys.toList()

    fun spawnAll() {
        QuestsOG.config.npcSpawns.values.forEach { spawn(it) }
    }

    /** @return True if the NPC is live; false if its world is not loaded yet (spawn deferred). */
    fun spawn(spawn: NpcSpawn): Boolean {
        despawn(spawn.tier)
        val location = spawn.toLocation()
        if (location == null) {
            pendingWorlds[spawn.tier] = spawn
            if (debug) QuestsOG.plugin.logger.info("Deferring NPC tier ${spawn.tier}: world ${spawn.world} not loaded")
            return false
        }

        val npc = NPC(location, QuestNpcNames.npcName(spawn.tier))
        npc.setOption(NpcOption.SHOW_TAB_LIST, false)
        npc.setOption(NpcOption.COLLISION, false)
        npc.setOption(NpcOption.LOOK_AT_PLAYER, LOOK_AT_RANGE)
        spawn.skin?.let { npc.setOption(NpcOption.SKIN, NpcSkin.of(Skin(null, it.value, it.signature))) }
        npc.setClickEvent(QuestNpcClickAction(spawn.tier))
        npc.setEnabled(true)
        npc.showNpcToAllPlayers()

        npcs[spawn.tier] = QuestNpc(spawn.tier, npc)
        if (debug)
            QuestsOG.plugin.logger.info(
                "Spawned NPC tier ${spawn.tier} at ${spawn.world} ${spawn.x} ${spawn.y} ${spawn.z}"
            )
        return true
    }

    fun despawn(tier: Int) {
        pendingWorlds.remove(tier)
        npcs.remove(tier)?.let {
            it.remove()
            if (debug) QuestsOG.plugin.logger.info("Despawned NPC tier $tier")
        }
    }

    fun despawnAll() {
        tiers().forEach { despawn(it) }
    }

    /** Spawns (or moves) a tier from the current config, or despawns it if the config no longer has it. */
    fun respawnFromConfig(tier: Int) {
        val spawn = QuestsOG.config.npcSpawns[tier]
        if (spawn == null) despawn(tier) else spawn(spawn)
    }

    fun reload() {
        despawnAll()
        Config.reloadNpcSpawns()
        spawnAll()
    }

    fun onWorldLoaded(world: World) {
        pendingWorlds.values.filter { it.world == world.name }.forEach { spawn(it) }
    }

    fun refreshNames(player: Player) {
        npcs.values.forEach { it.refreshName(player) }
    }

    fun refreshNamesForAll() {
        Bukkit.getOnlinePlayers().forEach(::refreshNames)
    }

    private companion object {
        /** Blocks within which an NPC turns its head to follow the viewer. */
        const val LOOK_AT_RANGE = 8.0
    }
}
