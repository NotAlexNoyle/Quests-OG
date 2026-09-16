package net.trueog.questsOG.npc

import de.eisi05.npc.api.objects.NPC
import java.io.IOException
import net.trueog.questsOG.QuestsOG
import net.trueog.questsOG.progression.HomesProgression
import net.trueog.questsOG.quests.Quest
import org.bukkit.entity.Player

/** One spawned home-tier NPC. Tier 1 is the greeter and has no quest. */
class QuestNpc(val tier: Int, val npc: NPC) {
    val quest: Quest?
        get() = HomesProgression.quests.getOrNull(tier - 2)

    /** Re-evaluates this NPC's nametag for one viewer. Main thread only. */
    fun refreshName(player: Player) {
        if (npc.viewers.contains(player.uniqueId)) npc.updateName(player)
    }

    fun remove() {
        try {
            npc.delete()
        } catch (e: IOException) {
            QuestsOG.plugin.logger.warning("Failed to remove quest NPC for tier $tier: ${e.message}")
        }
    }
}
