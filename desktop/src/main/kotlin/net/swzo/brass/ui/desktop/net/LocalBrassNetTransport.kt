package net.swzo.brass.ui.desktop.net

import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.Dispatchers
import net.swzo.brass.ui.kit.net.AuthContext
import net.swzo.brass.ui.kit.net.AuthDecision
import net.swzo.brass.ui.kit.net.BrassAction
import net.swzo.brass.ui.kit.net.BrassActionResult
import net.swzo.brass.ui.kit.net.BrassNet
import net.swzo.brass.ui.kit.net.BrassNetTransport
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * The desktop [BrassNetTransport]: the "server" is this process.
 *
 * Actions dispatch to the same core registry the game server uses - [BrassNet.dispatch] runs the
 * authorizer, rate limiter and the handler itself - on a small worker pool, so a slow handler never
 * freezes the render thread. Replies and state updates are marshaled back to the GLFW/render thread
 * through UniversalCraft's main dispatcher, which is the desktop equivalent of the game's
 * `Minecraft.execute`.
 *
 * Identity is config-driven for demos and tests:
 * `-Dbrassui.net.user=Steve -Dbrassui.net.op=4`.
 */
class LocalBrassNetTransport : BrassNetTransport {

    private val user = System.getProperty("brassui.net.user") ?: "local"
    private val opLevel = System.getProperty("brassui.net.op")?.toIntOrNull() ?: 0

    private val threadIds = AtomicInteger()
    private val pool = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "brassui-net-${threadIds.incrementAndGet()}").apply { isDaemon = true }
    }

    private val subscribers = ConcurrentHashMap<String, CopyOnWriteArrayList<(ByteArray?) -> Unit>>()

    override val name = "local"
    override val local = true

    override val identity: String = "$user (op $opLevel)"

    override fun can(action: BrassAction<*>): AuthDecision =
        if (opLevel >= action.minOpLevel) AuthDecision.Grant
        else AuthDecision.Deny("requires op level ${action.minOpLevel}")

    override fun sendAction(
        requestId: Long,
        actionId: String,
        data: ByteArray?,
        reply: (BrassActionResult) -> Unit,
    ) {
        pool.execute {
            // Dispatch itself is synchronous until the handler; async handlers complete on their own
            // threads, so marshal back to the render thread wherever the future completes.
            BrassNet.dispatch(actionId, data, AuthContext(user, opLevel))
                .thenAccept { result -> onUiThread { reply(result) } }
        }
    }

    override fun subscribe(stateId: String, onUpdate: (ByteArray?) -> Unit): () -> Unit {
        val list = subscribers.computeIfAbsent(stateId) { CopyOnWriteArrayList() }
        list.add(onUpdate)
        // Deliver the current value (or null when the id is unknown) right away - the in-process
        // equivalent of the game server's snapshot-on-subscribe.
        onUiThread { onUpdate(BrassNet.snapshot(stateId)) }
        return { list.remove(onUpdate) }
    }

    override fun publish(stateId: String, data: ByteArray?, toPlayer: String?) {
        val list = subscribers[stateId] ?: return
        onUiThread { for (onUpdate in list) onUpdate(data) }
    }

    override fun onUiThread(runnable: Runnable) {
        try {
            // UniversalCraft registers its GLFW/render thread as kotlinx-coroutines' Main dispatcher,
            // so Dispatchers.Main is the desktop equivalent of Minecraft.getInstance().execute.
            Dispatchers.Main.dispatch(EmptyCoroutineContext, runnable)
        } catch (throwable: Throwable) {
            // Not running under UniversalCraft (tests): run inline rather than dropping the work.
            runnable.run()
        }
    }
}
