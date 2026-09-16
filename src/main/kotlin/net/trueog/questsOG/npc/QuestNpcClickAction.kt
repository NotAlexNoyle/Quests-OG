package net.trueog.questsOG.npc

import de.eisi05.npc.api.events.NpcInteractEvent
import de.eisi05.npc.api.interfaces.NpcClickAction
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.launch
import net.trueog.questsOG.MainThreadBlock
import net.trueog.questsOG.QuestMenus
import net.trueog.questsOG.QuestsOG
import net.trueog.utilitiesog.UtilitiesOG

/**
 * Handles clicks on a home-tier NPC. NpcApi dispatches these on the main thread. The greeter talks; every other tier
 * opens the quest menu directly on its own quest.
 */
class QuestNpcClickAction(private val tier: Int) : NpcClickAction {
    override fun call(event: NpcInteractEvent) {
        val player = event.player
        if (!debounce(player.uniqueId)) return
        if (QuestsOG.config.debug) QuestsOG.plugin.logger.info("${player.name} clicked quest NPC tier $tier")

        if (tier == QuestNpcNames.GREETER_TIER) {
            QuestNpcNames.GREETER_LINES.forEach {
                UtilitiesOG.trueogMessage(player, it.replace("<player>", player.name) + "<reset>")
            }
            return
        }

        QuestsOG.scope.launch {
            try {
                val builder = QuestMenus.homesBuilder(player)
                MainThreadBlock.runOnMainThread { builder.openSection(tier - 2) }
            } catch (t: Throwable) {
                QuestsOG.plugin.logger.severe("Quest NPC click (tier $tier) failed: ${t.message}")
                if (QuestsOG.config.debug) t.printStackTrace()
            }
        }
    }

    private companion object {
        /** A single interaction can arrive as both a left and a right click; collapse them. */
        const val DEBOUNCE_MILLIS = 500L
        val lastClick = ConcurrentHashMap<UUID, Long>()

        fun debounce(uuid: UUID): Boolean {
            val now = System.currentTimeMillis()
            val previous = lastClick.put(uuid, now)
            return previous == null || now - previous >= DEBOUNCE_MILLIS
        }
    }
}
