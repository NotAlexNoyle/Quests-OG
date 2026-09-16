package net.trueog.questsOG

import kotlinx.coroutines.launch
import net.trueog.questsOG.progression.HomesProgression
import net.trueog.utilitiesog.UtilitiesOG
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ClaimQuest : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val debug = QuestsOG.config.debug
        if (debug) QuestsOG.plugin.logger.info("/claimquest invoked by ${sender.name}")
        if (sender !is Player) {
            sender.sendMessage("ERROR: You can only execute this command as a player.")
            return true
        }

        val nextQuest = HomesProgression.getNextQuest(sender)
        if (debug) QuestsOG.plugin.logger.info("/claimquest nextQuest=${nextQuest?.javaClass?.simpleName}")
        if (nextQuest == null) {
            UtilitiesOG.trueogMessage(sender, "<green>You have completed all available quests.<reset>")
            return true
        }

        QuestsOG.scope.launch {
            try {
                val result = QuestClaimer.claim(sender, nextQuest)
                if (debug) QuestsOG.plugin.logger.info("/claimquest result=$result")
                QuestClaimer.notify(sender, result)
                if (result is QuestClaimer.Result.Claimed) {
                    MainThreadBlock.runOnMainThread { QuestsOG.questNpcs.refreshNames(sender) }
                }
            } catch (t: Throwable) {
                QuestsOG.plugin.logger.severe("/claimquest failed: ${t.message}")
                if (debug) t.printStackTrace()
            }
        }
        return true
    }
}
