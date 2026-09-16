package net.trueog.questsOG

import kotlinx.coroutines.launch
import net.trueog.gxui.progress.ProgressMenu
import net.trueog.questsOG.progression.HomesProgression
import net.trueog.questsOG.quests.Quest
import org.bukkit.Material
import org.bukkit.entity.Player

/** Builds the home quest progress menu shared by /questgui and the quest NPCs. */
object QuestMenus {
    /** Suspends while fetching requirements; open the returned builder on the main thread. */
    suspend fun homesBuilder(player: Player): ProgressMenu.Builder {
        val debug = QuestsOG.config.debug
        val nextQuest = HomesProgression.getNextQuest(player)
        if (debug)
            QuestsOG.plugin.logger.info("Quest menu for ${player.name}: nextQuest=${nextQuest?.javaClass?.simpleName}")
        val builder = ProgressMenu.builder(QuestsOG.plugin, player, "&5Home Quests")

        HomesProgression.quests.forEach { quest ->
            val state = stateFor(quest, nextQuest)
            builder.section(questName(quest), state)
            quest.getRequirements(player)?.forEach { requirement ->
                when (requirement) {
                    is ProgressRequirement ->
                        builder.progress(
                            materialFor(requirement),
                            requirement.name,
                            requirement.current,
                            requirement.target,
                        )
                    is BooleanRequirement -> builder.goal(materialFor(requirement), requirement.name, requirement.met)
                }
            }
            when (state) {
                ProgressMenu.State.IN_PROGRESS -> {
                    builder.description("&7Click &aClaim &7when ready.")
                    builder.action(
                        Material.NETHER_STAR,
                        "&aClaim Quest",
                        listOf("&7Claims this quest once all", "&7requirements are met."),
                    ) { menu, clicker ->
                        onClaimClicked(menu, clicker, quest)
                    }
                }
                ProgressMenu.State.COMPLETE -> builder.description("&aCompleted.")
                ProgressMenu.State.NOT_STARTED -> builder.description("&7Complete the previous quest first.")
            }
        }

        return builder
    }

    /** Inventory click handler (main thread): claim off-thread, then report back into the still-open menu. */
    private fun onClaimClicked(menu: ProgressMenu, player: Player, quest: Quest) {
        val debug = QuestsOG.config.debug
        QuestsOG.scope.launch {
            try {
                val result = QuestClaimer.claim(player, quest)
                if (debug) QuestsOG.plugin.logger.info("Claim click by ${player.name}: $result")
                MainThreadBlock.runOnMainThread {
                    if (result is QuestClaimer.Result.Claimed) {
                        player.closeInventory()
                        QuestsOG.questNpcs.refreshNames(player)
                    } else {
                        menu.showActionError(QuestClaimer.errorSummary(result))
                    }
                    QuestClaimer.notify(player, result)
                }
            } catch (t: Throwable) {
                QuestsOG.plugin.logger.severe("Claim click failed: ${t.message}")
                if (debug) t.printStackTrace()
            }
        }
    }

    fun questName(quest: Quest): String = "Home ${HomesProgression.getHomeCount(quest)} Quest"

    fun stateFor(quest: Quest, nextQuest: Quest?): ProgressMenu.State =
        when {
            nextQuest == null -> ProgressMenu.State.COMPLETE
            quest == nextQuest -> ProgressMenu.State.IN_PROGRESS
            HomesProgression.quests.indexOf(quest) < HomesProgression.quests.indexOf(nextQuest) ->
                ProgressMenu.State.COMPLETE
            else -> ProgressMenu.State.NOT_STARTED
        }

    fun materialFor(requirement: Requirement): Material =
        when (requirement.name) {
            "Total Shards" -> Material.DIAMOND
            "Hours Played",
            "Days Played" -> Material.CLOCK
            "Blocks Travelled" -> Material.LEATHER_BOOTS
            "Levels" -> Material.EXPERIENCE_BOTTLE
            "Duels Wins" -> Material.IRON_SWORD
            "Beaconator" -> Material.BEACON
            "A Furious Cocktail" -> Material.POTION
            "Serious Dedication" -> Material.NETHERITE_HOE
            "Died to \"death.fell.accident.water\"" -> Material.WATER_BUCKET
            "Blocks Travelled on Pig" -> Material.CARROT_ON_A_STICK
            "Blocks Travelled on Strider" -> Material.WARPED_FUNGUS_ON_A_STICK
            "Dolphins Killed" -> Material.DOLPHIN_SPAWN_EGG
            "Zoglins Killed" -> Material.ZOGLIN_SPAWN_EGG
            "The Cutest Predator" -> Material.AXOLOTL_BUCKET
            "Two by Two" -> Material.WHEAT
            "A Complete Catalogue" -> Material.CAT_SPAWN_EGG
            "Monsters Hunted" -> Material.DIAMOND_SWORD
            "Died to \"fell while climbing\"" -> Material.LADDER
            "Died to \"walked into the danger zone due to Zoglin\"" -> Material.MAGMA_BLOCK
            "Fish Caught" -> Material.FISHING_ROD
            "Has Villager Head" -> Material.PLAYER_HEAD
            "Blocks Walked on Water" -> Material.ICE
            "Blocks Walked Under Water" -> Material.PRISMARINE
            "Music Discs" -> Material.JUKEBOX
            "Finished Advancements" -> Material.RED_CONCRETE
            "Obsidian Mined" -> Material.OBSIDIAN
            "Dragon Eggs" -> Material.DRAGON_EGG
            else -> Material.PAPER
        }
}
