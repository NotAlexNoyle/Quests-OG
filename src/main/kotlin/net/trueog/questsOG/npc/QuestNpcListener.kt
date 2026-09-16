package net.trueog.questsOG.npc

import net.luckperms.api.event.user.UserDataRecalculateEvent
import net.trueog.questsOG.QuestsOG
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.WorldLoadEvent

/**
 * Keeps the registry in sync with the server: spawns NPCs whose world loads late and re-renders nametags once LuckPerms
 * applies a new homes permission. Join refresh needs no handler: NpcApi shows NPCs (evaluating names) after join.
 */
class QuestNpcListener(private val registry: QuestNpcRegistry) : Listener {
    @EventHandler
    fun onWorldLoad(event: WorldLoadEvent) {
        registry.onWorldLoaded(event.world)
    }

    /** LuckPerms fires this off the main thread and after reward() lands, so hop back before touching packets. */
    fun registerLuckPerms() {
        QuestsOG.luckPerms.eventBus.subscribe(QuestsOG.plugin, UserDataRecalculateEvent::class.java) { event ->
            val player = Bukkit.getPlayer(event.user.uniqueId) ?: return@subscribe
            Bukkit.getScheduler().runTask(QuestsOG.plugin, Runnable { registry.refreshNames(player) })
        }
    }
}
