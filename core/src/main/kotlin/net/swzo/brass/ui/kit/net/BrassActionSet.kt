package net.swzo.brass.ui.kit.net

/**
 * Marks a class - normally a Kotlin `object` - whose `brassAction` properties make up an action set.
 *
 * The annotation is what each transport's discovery scans for (FML scan data in NeoForge, classpath
 * entries on the desktop). Loading the class runs its property initializers, which is what registers
 * every action. Implement [BrassActions] as well so discovery can type-check the loaded class.
 */
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

    /**
     * Load and initialise the class named [className] if it is an action set. Returns true when the
     * class was found and exposes an instance implementing [BrassActions].
     */
    fun load(className: String): Boolean {
        val clazz = runCatching {
            Class.forName(className, true, BrassActionSets::class.java.classLoader)
        }.getOrNull() ?: return false
        if (!BrassActions::class.java.isAssignableFrom(clazz)) return false
        val instance = runCatching { clazz.getField("INSTANCE").get(null) }.getOrNull()
            ?: runCatching { clazz.getDeclaredConstructor().newInstance() }.getOrNull()
        return instance is BrassActions
    }

    /** Load every name in [classNames]; returns how many were action sets. */
    fun loadAll(classNames: Collection<String>): Int = classNames.count { load(it) }
}
