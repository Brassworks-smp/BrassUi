package net.swzo.brass.ui.kit.net

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class BrassActionSet

/** Marker interface for action-set objects - pair with the [BrassActionSet] annotation. */
interface BrassActions

/**
 * Loads discovered action-set classes and (as a side effect of their initialisation) registers their
 * actions. This is the only registration path a developer ever needs: discovery finds the class,
 * [load] touches it, the `brassAction` properties register themselves.
 */
object BrassActionSets {

    fun load(className: String): Boolean {
        val clazz = runCatching {
            Class.forName(className, true, BrassActionSets::class.java.classLoader)
        }.getOrNull() ?: return false
        if (!BrassActions::class.java.isAssignableFrom(clazz)) return false
        val instance = runCatching { clazz.getField("INSTANCE").get(null) }.getOrNull()
            ?: runCatching { clazz.getDeclaredConstructor().newInstance() }.getOrNull()
        return instance is BrassActions
    }

    fun loadAll(classNames: Collection<String>): Int = classNames.count { load(it) }
}
