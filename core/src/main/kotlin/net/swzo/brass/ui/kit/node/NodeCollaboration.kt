package net.swzo.brass.ui.kit.node

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max

data class NodeCollaborativeEdit(
    val actorId: String,
    val clock: Long,
    val label: String,
    val graphJson: String,
)

/**
 * Transport boundary for collaboration. WebSocket, IPC and game-network adapters only need publish
 * and subscribe; ordering, echo suppression and deterministic conflict resolution live in
 * [NodeCollaborationSession].
 */
interface NodeCollaborationTransport {
    fun publish(edit: NodeCollaborativeEdit)
    fun subscribe(listener: (NodeCollaborativeEdit) -> Unit): () -> Unit
}

interface NodeCollaborativeDocument {
    fun onGraphChange(listener: (GraphChange) -> Unit): () -> Unit
    fun applyRemoteSnapshot(json: String, label: String = "Remote edit")
}

/**
 * A Lamport-clock last-writer-wins session. Concurrent edits use actor ID as a stable tie-breaker, so
 * every peer converges on the same snapshot even when two transports deliver messages in a different
 * order. [dispatch] lets desktop/game hosts marshal remote graph application onto their UI thread.
 */
class NodeCollaborationSession(
    private val editor: NodeCollaborativeDocument,
    val actorId: String,
    private val transport: NodeCollaborationTransport,
    private val dispatch: (() -> Unit) -> Unit = { it() },
) : AutoCloseable {
    private data class Stamp(val clock: Long, val actorId: String) : Comparable<Stamp> {
        override fun compareTo(other: Stamp): Int =
            compareValuesBy(this, other, Stamp::clock, Stamp::actorId)
    }

    private var clock = 0L
    private var latest = Stamp(0, "")
    @Volatile private var applyingRemote = false
    @Volatile private var closed = false
    private val conflicts = CopyOnWriteArrayList<(NodeCollaborativeEdit) -> Unit>()
    private val stopLocal = editor.onGraphChange(::publishLocal)
    private val stopRemote = transport.subscribe(::receive)

    fun onConflict(listener: (NodeCollaborativeEdit) -> Unit): () -> Unit {
        conflicts += listener
        return { conflicts -= listener }
    }

    private fun publishLocal(change: GraphChange) {
        if (applyingRemote || closed) return
        val edit: NodeCollaborativeEdit
        synchronized(this) {
            clock++
            latest = Stamp(clock, actorId)
            edit = NodeCollaborativeEdit(actorId, clock, change.label, change.graphJson)
        }
        transport.publish(edit)
    }

    fun receive(edit: NodeCollaborativeEdit) {
        if (closed || edit.actorId == actorId) return
        val incoming = Stamp(edit.clock, edit.actorId)
        val shouldApply: Boolean
        val concurrent: Boolean
        synchronized(this) {
            concurrent = incoming.clock == latest.clock && incoming.actorId != latest.actorId
            shouldApply = incoming > latest
            clock = max(clock, edit.clock)
            if (shouldApply) latest = incoming
        }
        if (concurrent) conflicts.forEach { listener -> runCatching { listener(edit) } }
        if (!shouldApply) return
        dispatch {
            if (closed) return@dispatch
            synchronized(this) {
                if (latest != incoming) return@dispatch
            }
            applyingRemote = true
            try {
                editor.applyRemoteSnapshot(edit.graphJson, "${edit.label} · ${edit.actorId}")
            } finally {
                applyingRemote = false
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        stopLocal()
        stopRemote()
        conflicts.clear()
    }
}

/** In-process transport useful for tests, split-screen editors and local preview tools. */
class InMemoryNodeCollaborationHub {
    private val listeners = CopyOnWriteArrayList<(NodeCollaborativeEdit) -> Unit>()

    fun transport(): NodeCollaborationTransport = object : NodeCollaborationTransport {
        override fun publish(edit: NodeCollaborativeEdit) {
            listeners.forEach { listener -> listener(edit) }
        }

        override fun subscribe(listener: (NodeCollaborativeEdit) -> Unit): () -> Unit {
            listeners += listener
            return { listeners -= listener }
        }
    }
}
