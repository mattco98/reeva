package me.mattco.reeva

import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.Realm
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object Reeva {
    private val agents = ThreadLocal<Agent>()
    internal val allAgents = mutableListOf<Agent>()

    @JvmStatic
    val activeAgent: Agent
        get() = agents.get()

    internal var running = false
        private set

    val threadPool: ExecutorService = Executors.newFixedThreadPool(10)

    val PRINT_PARSE_NODES = System.getProperty("reeva.debugParseNodes")?.toBoolean() ?: false
    var EMIT_CLASS_FILES = true
        internal set

    @JvmStatic
    fun teardown() {
        running = false
        threadPool.shutdownNow()
    }

    @JvmStatic
    fun makeRealm() = activeAgent.hostHooks.initializeHostDefinedRealm()

    @JvmStatic
    fun setAgent(agent: Agent) {
        agents.set(agent)
    }

    @JvmStatic
    fun setup() {
        Realm.setupSymbols()
        running = true
    }
}
