package net.trueog.questsOG.npc

import de.eisi05.npc.api.objects.NpcName
import de.eisi05.npc.api.utils.SerializableFunction
import de.eisi05.npc.api.wrapper.objects.WrappedComponent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.trueog.questsOG.Config
import net.trueog.questsOG.QuestsOG
import net.trueog.questsOG.progression.HomesProgression
import org.bukkit.entity.Player

/** Nametags and greeter text for the home-tier NPCs. Nametags are evaluated per viewer on the main thread. */
object QuestNpcNames {
    const val GREETER_TIER = Config.MIN_TIER

    private const val GREETER_NAME = "<light_purple>Quest Master"

    /** Sent to a player who clicks the greeter. `<player>` is replaced with their name. */
    val GREETER_LINES =
        listOf(
            "<gold>Welcome, <yellow><player><gold>! Extra homes are earned by completing quests.",
            "<gold>Each quest giver near me unlocks one more home. Talk to them to see what they ask for.",
            "<gold>You can also check your progress any time with <yellow>/questgui<gold>.",
        )

    private val legacy = LegacyComponentSerializer.legacySection()

    fun npcName(tier: Int): NpcName =
        if (tier == GREETER_TIER) NpcName.ofLegacy(toLegacy(GREETER_NAME))
        else NpcName.of(TierName(tier), WrappedComponent.parseFromLegacy(toLegacy(lockedName(tier))))

    /**
     * Viewer-specific nametag: completed once the player holds that many homes, current for the next tier, else locked.
     */
    fun nameFor(tier: Int, player: Player): String {
        val homes = HomesProgression.getCurrentHomeCount(player)
        return when {
            homes >= tier -> "<green>Home $tier Quest ✔"
            homes == tier - 1 -> "<yellow>Home $tier Quest"
            else -> lockedName(tier)
        }
    }

    private fun lockedName(tier: Int) = "<gray>Home $tier Quest ✖"

    private fun toLegacy(miniMessage: String): String = legacy.serialize(QuestsOG.mm.deserialize(miniMessage))

    /** Explicit class rather than a lambda: NpcApi requires Serializable functions, though we never persist NPCs. */
    private class TierName(private val tier: Int) : SerializableFunction<Player, WrappedComponent> {
        override fun apply(player: Player): WrappedComponent =
            WrappedComponent.parseFromLegacy(toLegacy(nameFor(tier, player)))
    }
}
