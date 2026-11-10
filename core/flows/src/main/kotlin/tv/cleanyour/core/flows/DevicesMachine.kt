package tv.cleanyour.core.flows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.cleanyour.core.discovery.DiscoveryEvent
import tv.cleanyour.core.discovery.FoundTv
import tv.cleanyour.core.model.ConnectionState
import tv.cleanyour.core.model.TvId

// Upgrade Your TV: "Devices". Every TV and box this phone has set up (each keeps its own settings,
// switched-off list, look and restore points under its own id) and every Android TV device the search
// finds on this Wi-Fi now. A known device shows whether it is on the network (online) and whether this
// app holds a connection to it; Connect opens one (the debugging step if needed) and makes it the device
// Manage shows, Disconnect closes it and forgets nothing. A device that is found but not set up yet
// leads into Setup. The search starts again each time the screen opens and on Refresh.
data class KnownDevice(
    val id: TvId,
    val name: String,
    val room: String?,
    val model: String,
    val host: String?,          // the address it was last seen at
    val online: Boolean,        // the search sees it on this Wi-Fi now
    val connected: Boolean,     // this app holds a connection now
    val connecting: Boolean,
)

data class NewDevice(val host: String, val name: String, val model: String?, val debuggingOpen: Boolean?)

data class DevicesState(
    val known: List<KnownDevice> = emptyList(),
    val found: List<NewDevice> = emptyList(),
    val onWifi: Boolean = true,
    val searching: Boolean = true,
    val busy: TvId? = null,          // connecting or disconnecting this one
    val failed: TvId? = null,        // the last Connect that did not get through
)

sealed interface DevicesIntent {
    data object Refresh : DevicesIntent
    data class Connect(val id: TvId) : DevicesIntent
    data class Disconnect(val id: TvId) : DevicesIntent
    data class Open(val id: TvId) : DevicesIntent          // show it in Manage
    data object SetUpNew : DevicesIntent
}

class DevicesMachine(
    private val d: ManageDeps,
    scope: CoroutineScope,
) : Machine<DevicesState, DevicesIntent, ManageEffect>(DevicesState(), scope) {

    private data class Update(val f: (DevicesState) -> DevicesState)
    private val seen = LinkedHashMap<String, FoundTv>()
    private var search: Job? = null

    init {
        scope.launch { d.tvs.tvs.collect { rebuild() } }
        scope.launch { d.sessions.states.collect { rebuild() } }
        startSearch()
    }

    override fun reduce(state: DevicesState, event: Any): DevicesState = when (event) {
        is Update -> event.f(state)
        else -> state
    }

    override fun handle(event: Any, before: DevicesState, after: DevicesState) {
        when (event) {
            DevicesIntent.Refresh -> startSearch()
            is DevicesIntent.Connect -> if (before.busy == null) scope.launch { connect(event.id) }
            is DevicesIntent.Disconnect -> if (before.busy == null) scope.launch { disconnect(event.id) }
            is DevicesIntent.Open -> effect(ManageEffect.Navigate("manage/${event.id.value}"))
            DevicesIntent.SetUpNew -> effect(ManageEffect.Navigate("setup"))
            else -> {}
        }
    }

    // A fresh search: what was seen before is dropped, so a device that left the network leaves the list.
    private fun startSearch() {
        search?.cancel()
        synchronized(seen) { seen.clear() }
        send(Update { it.copy(searching = true) })
        rebuild()
        search = scope.launch {
            launch { delay(SEARCH_SETTLE_MS); send(Update { it.copy(searching = false) }) }
            attempt {
                d.discovery.discover().collect { e ->
                    when (e) {
                        is DiscoveryEvent.Found -> synchronized(seen) { seen[e.tv.host] = e.tv }
                        is DiscoveryEvent.Updated -> synchronized(seen) { seen[e.tv.host] = e.tv }
                        is DiscoveryEvent.Lost -> synchronized(seen) { seen.remove(e.host) }
                        is DiscoveryEvent.NetworkState -> send(Update { it.copy(onWifi = e.onWifi) })
                    }
                    rebuild()
                }
            }
        }
    }

    private fun rebuild() {
        val tvs = d.tvs.tvs.value
        val states = d.sessions.states.value
        val now = synchronized(seen) { seen.values.toList() }
        val hostsOnline = now.map { it.host }.toSet()
        val knownHosts = HashSet<String>()
        val known = tvs.map { tv ->
            val hosts = tv.endpoints.map { it.host }
            knownHosts += hosts
            val st = states[tv.id]
            val connected = st is ConnectionState.Connected
            KnownDevice(
                id = tv.id, name = tv.nickname, room = tv.room, model = tv.facts.model,
                host = (st as? ConnectionState.Connected)?.endpoint?.host ?: hosts.firstOrNull { it in hostsOnline } ?: hosts.firstOrNull(),
                online = connected || hosts.any { it in hostsOnline },
                connected = connected,
                connecting = st is ConnectionState.Connecting || st is ConnectionState.AwaitingAllow || st is ConnectionState.Reconnecting,
            )
        }.sortedWith(compareBy<KnownDevice>({ !it.connected }, { !it.online }, { it.name.lowercase() }))
        val found = now.filter { it.host !in knownHosts }
            .map { NewDevice(it.host, it.friendlyName ?: it.model ?: it.host, it.model, it.port5555Open) }
            .sortedBy { it.name.lowercase() }
        send(Update { it.copy(known = known, found = found) })
    }

    private suspend fun connect(id: TvId) {
        send(Update { it.copy(busy = id, failed = null) })
        val r = d.access.session(id)
        send(Update { it.copy(busy = null, failed = if (r is AccessResult.Ok) null else id) })
        rebuild()
        if (r is AccessResult.Ok) effect(ManageEffect.Navigate("manage/${id.value}"))
    }

    // Closes this app's connection. The device, its settings and its restore points stay.
    private suspend fun disconnect(id: TvId) {
        send(Update { it.copy(busy = id, failed = null) })
        attempt { d.sessions.existing(id)?.close() }
        send(Update { it.copy(busy = null) })
        rebuild()
    }

    companion object {
        const val SEARCH_SETTLE_MS = 6_000L
    }
}
