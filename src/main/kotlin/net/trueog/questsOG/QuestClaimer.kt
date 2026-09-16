package net.trueog.questsOG

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.trueog.questsOG.progression.HomesProgression
import net.trueog.questsOG.quests.Quest
import net.trueog.utilitiesog.UtilitiesOG
import org.bukkit.entity.Player

/** Shared claim flow used by /claimquest and the quest menu's Claim button. */
object QuestClaimer {
    sealed interface Result {
        data class Claimed(val homeCount: Int) : Result

        /** The quest is no longer the player's next one (already claimed, or menu was stale). */
        data object NotCurrentQuest : Result

        data object EligibilityUnknown : Result

        data object NotEligible : Result

        data object ConsumeFailed : Result

        /** Another claim for this player is still running. */
        data object Busy : Result
    }

    private val inFlight: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    suspend fun claim(player: Player, quest: Quest): Result {
        if (!inFlight.add(player.uniqueId)) return Result.Busy
        try {
            if (HomesProgression.getNextQuest(player) != quest) return Result.NotCurrentQuest

            val isEligible = quest.isEligible(player) ?: return Result.EligibilityUnknown
            if (!isEligible) return Result.NotEligible

            if (!quest.consumeQuestItems(player)) return Result.ConsumeFailed
            quest.reward(player)

            val homeCount = HomesProgression.getHomeCount(quest)
            UtilitiesOG.logToConsole("[Quests-OG]", "${player.name} claimed quest ${quest::class.simpleName}")
            return Result.Claimed(homeCount)
        } finally {
            inFlight.remove(player.uniqueId)
        }
    }

    fun notify(player: Player, result: Result) {
        val message =
            when (result) {
                is Result.Claimed ->
                    "<green>Claimed quest! You now have <yellow>${result.homeCount}<green> homes.<reset>"
                Result.NotCurrentQuest -> "<red>That quest is not your next quest.<reset>"
                Result.EligibilityUnknown ->
                    "<red>Something went wrong while checking your quest eligibility. Contact an administrator.<reset>"
                Result.NotEligible -> "<red>You must meet all the quest's requirements first.<reset>"
                Result.ConsumeFailed -> "<red>Something wrong while trying to consume the quest items.<reset>"
                Result.Busy -> "<red>Your previous claim is still being processed.<reset>"
            }
        UtilitiesOG.trueogMessage(player, message)
    }

    /** Short, uncolored text for the menu's error pane. */
    fun errorSummary(result: Result): String =
        when (result) {
            is Result.Claimed -> "Claimed!"
            Result.NotCurrentQuest -> "This is not your next quest."
            Result.EligibilityUnknown -> "Could not check your eligibility."
            Result.NotEligible -> "You must meet all requirements first."
            Result.ConsumeFailed -> "Could not consume the quest items."
            Result.Busy -> "Your previous claim is still processing."
        }
}
