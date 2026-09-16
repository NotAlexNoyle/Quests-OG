package net.trueog.questsOG.npc

import net.trueog.questsOG.Config
import net.trueog.questsOG.NpcSpawn
import net.trueog.questsOG.QuestsOG
import net.trueog.utilitiesog.UtilitiesOG
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/** Admin command for placing the home-tier NPCs. Positions persist to config.yml. */
class QuestNpcCommand(private val registry: QuestNpcRegistry) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission(PERMISSION)) {
            reply(sender, "<red>You do not have permission to do that.<reset>")
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {
            "set" -> set(sender, args)
            "remove" -> remove(sender, args)
            "tp" -> teleport(sender, args)
            "list" -> list(sender)
            "reload" -> {
                registry.reload()
                reply(sender, "<green>Reloaded quest NPCs from config.<reset>")
            }
            else -> reply(sender, "<yellow>Usage: /$label <set|remove|tp|list|reload> [tier]<reset>")
        }
        return true
    }

    private fun set(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player ?: return playerOnly(sender)
        val tier = parseTier(sender, args) ?: return
        val location = player.location
        val existingSkin = QuestsOG.config.npcSkins[tier]
        Config.writeNpcSpawn(
            NpcSpawn(
                tier,
                location.world.name,
                location.x,
                location.y,
                location.z,
                location.yaw,
                location.pitch,
                existingSkin,
            )
        )
        registry.respawnFromConfig(tier)
        UtilitiesOG.trueogMessage(
            sender,
            "<green>Placed the tier <yellow>$tier<green> quest NPC at your location.<reset>",
        )
    }

    private fun remove(sender: CommandSender, args: Array<out String>) {
        val tier = parseTier(sender, args) ?: return
        if (QuestsOG.config.npcSpawns[tier] == null) {
            reply(sender, "<red>No NPC is configured for tier $tier.<reset>")
            return
        }
        Config.deleteNpcSpawn(tier)
        registry.despawn(tier)
        reply(sender, "<green>Removed the tier <yellow>$tier<green> quest NPC.<reset>")
    }

    private fun teleport(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player ?: return playerOnly(sender)
        val tier = parseTier(sender, args) ?: return
        val npc = registry.get(tier)
        if (npc == null) {
            reply(sender, "<red>The tier $tier quest NPC is not spawned.<reset>")
            return
        }
        player.teleport(npc.npc.location)
        reply(sender, "<green>Teleported to the tier <yellow>$tier<green> quest NPC.<reset>")
    }

    private fun list(sender: CommandSender) {
        reply(sender, "<gold>Quest NPCs:<reset>")
        for (tier in Config.MIN_TIER..Config.MAX_TIER) {
            val spawn = QuestsOG.config.npcSpawns[tier]
            val skin = if (QuestsOG.config.npcSkins[tier] != null) "custom skin" else "default skin"
            val line =
                if (spawn == null) {
                    "<gray>Tier $tier: not placed ($skin)"
                } else {
                    val state = if (registry.get(tier) != null) "<green>spawned" else "<red>waiting for world"
                    val coords = "%.1f %.1f %.1f".format(spawn.x, spawn.y, spawn.z)
                    "<yellow>Tier $tier: $state <gray>${spawn.world} $coords ($skin)"
                }
            reply(sender, "$line<reset>")
        }
    }

    private fun parseTier(sender: CommandSender, args: Array<out String>): Int? {
        val tier = args.getOrNull(1)?.toIntOrNull()
        if (tier == null || tier < Config.MIN_TIER || tier > Config.MAX_TIER) {
            reply(sender, "<red>Tier must be a number from ${Config.MIN_TIER} to ${Config.MAX_TIER}.<reset>")
            return null
        }
        return tier
    }

    private fun playerOnly(sender: CommandSender) {
        reply(sender, "<red>Only players can use this subcommand.<reset>")
    }

    /** Console has no Player, so expand MiniMessage ourselves for non-player senders. */
    private fun reply(sender: CommandSender, message: String) {
        if (sender is Player) UtilitiesOG.trueogMessage(sender, message)
        else sender.sendMessage(UtilitiesOG.trueogExpand(message))
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        if (!sender.hasPermission(PERMISSION)) return emptyList()
        return when (args.size) {
            1 -> SUBCOMMANDS.filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> if (args[0].lowercase() in TIER_SUBCOMMANDS) TIERS.filter { it.startsWith(args[1]) } else emptyList()
            else -> emptyList()
        }
    }

    private companion object {
        const val PERMISSION = "questsog.admin"
        val SUBCOMMANDS = listOf("set", "remove", "tp", "list", "reload")
        val TIER_SUBCOMMANDS = setOf("set", "remove", "tp")
        val TIERS = (Config.MIN_TIER..Config.MAX_TIER).map(Int::toString)
    }
}
