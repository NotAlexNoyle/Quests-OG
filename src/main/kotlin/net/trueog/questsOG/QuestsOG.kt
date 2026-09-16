package net.trueog.questsOG

import de.eisi05.npc.api.NpcApi
import de.eisi05.npc.api.objects.NpcConfig
import kotlinx.coroutines.*
import me.realized.duels.api.Duels
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.luckperms.api.LuckPerms
import net.trueog.diamondbankog.api.DiamondBankAPIKotlin
import net.trueog.questsOG.npc.QuestNpcCommand
import net.trueog.questsOG.npc.QuestNpcListener
import net.trueog.questsOG.npc.QuestNpcRegistry
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin

class QuestsOG : JavaPlugin() {
    companion object {
        lateinit var scope: CoroutineScope

        lateinit var plugin: QuestsOG
        lateinit var config: Config
        lateinit var redis: Redis
        lateinit var diamondBankAPI: DiamondBankAPIKotlin
        lateinit var luckPerms: LuckPerms
        lateinit var duels: Duels
        lateinit var mobHeads: Plugin
        lateinit var questNpcs: QuestNpcRegistry
        var mm =
            MiniMessage.builder()
                .tags(TagResolver.builder().resolver(StandardTags.color()).resolver(StandardTags.reset()).build())
                .build()

        fun isRedisInitialized() = ::redis.isInitialized

        private var npcApiStarted = false
    }

    override fun onEnable() {
        plugin = this
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            logger.severe("Uncaught coroutine exception: ${throwable.message}")
            if (Companion.config.debug) throwable.printStackTrace()
        }
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + exceptionHandler)

        Companion.config =
            Config.create()
                ?: run {
                    Bukkit.getPluginManager().disablePlugin(this)
                    return
                }

        redis = Redis()
        if (!redis.testConnection()) {
            logger.severe("Could not connect to Redis")
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }

        val diamondBankAPIProvider = server.servicesManager.getRegistration(DiamondBankAPIKotlin::class.java)
        if (diamondBankAPIProvider == null) {
            logger.severe("DiamondBank-OG API is null")
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }
        diamondBankAPI = diamondBankAPIProvider.provider

        val luckPermsProvider = Bukkit.getServicesManager().getRegistration(LuckPerms::class.java)
        if (luckPermsProvider == null) {
            this.logger.severe("Luckperms API is null, quitting....")
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }
        luckPerms = luckPermsProvider.provider

        val duels = Bukkit.getServer().pluginManager.getPlugin("Duels-OG")
        if (duels == null) {
            this.logger.severe("Duels API is null, quitting....")
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }
        Companion.duels = duels as Duels

        val mobHeads = Bukkit.getServer().pluginManager.getPlugin("MobHeads-OG")
        if (mobHeads == null) {
            this.logger.severe("The MobHeads-OG plugin is not loaded, quitting....")
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }
        Companion.mobHeads = mobHeads

        this.server.pluginManager.registerEvents(Events(), this)
        getCommand("claimquest")?.setExecutor(ClaimQuest())
        getCommand("questgui")?.setExecutor(QuestGuiCommand())

        // NpcApi is shaded and hooks player connections, so it is torn down in onDisable. If it cannot
        // start (unsupported server build), quests stay usable through the commands; only NPCs are lost.
        questNpcs = QuestNpcRegistry()
        try {
            NpcApi.createInstance(this, NpcConfig().autoUpdate(false).debug(Companion.config.debug))
            npcApiStarted = true
        } catch (t: Throwable) {
            logger.severe("NpcApi failed to start, quest NPCs are disabled: ${t.message}")
            if (Companion.config.debug) t.printStackTrace()
            return
        }
        questNpcs.spawnAll()
        val npcListener = QuestNpcListener(questNpcs)
        npcListener.registerLuckPerms()
        this.server.pluginManager.registerEvents(npcListener, this)
        getCommand("questnpc")?.let {
            val questNpcCommand = QuestNpcCommand(questNpcs)
            it.setExecutor(questNpcCommand)
            it.tabCompleter = questNpcCommand
        }
    }

    override fun onDisable() {
        if (npcApiStarted) {
            questNpcs.despawnAll()
            NpcApi.disable()
            npcApiStarted = false
        }

        if (isRedisInitialized()) {
            redis.shutdown()
        }

        scope.cancel()

        runBlocking { scope.coroutineContext[Job]?.join() }
    }
}
