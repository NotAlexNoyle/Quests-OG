package net.trueog.questsOG

import kotlinx.coroutines.launch
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class QuestGuiCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val debug = QuestsOG.config.debug
        if (debug) QuestsOG.plugin.logger.info("/questgui invoked by ${sender.name}")
        if (sender !is Player) {
            sender.sendMessage("ERROR: You can only execute this command as a player.")
            return true
        }

        QuestsOG.scope.launch {
            try {
                val builder = QuestMenus.homesBuilder(sender)
                MainThreadBlock.runOnMainThread { builder.open() }
                if (debug) QuestsOG.plugin.logger.info("/questgui done")
            } catch (t: Throwable) {
                QuestsOG.plugin.logger.severe("/questgui failed: ${t.message}")
                if (debug) t.printStackTrace()
            }
        }

        return true
    }
}
