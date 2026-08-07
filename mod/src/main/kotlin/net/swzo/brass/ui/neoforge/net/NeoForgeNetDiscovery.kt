package net.swzo.brass.ui.neoforge.net

import net.neoforged.fml.ModList
import net.swzo.brass.ui.kit.net.BrassActionSet
import net.swzo.brass.ui.kit.net.BrassActionSets
import java.lang.annotation.ElementType

/**
 * Finds every `@BrassActionSet` object in every loaded mod - including this one - using the same scan
 * data FML already builds for `@Mod` and `@EventBusSubscriber`. The scan runs on both sides at payload
 * registration time, so a host mod writing an action set next to its screen gets it registered on the
 * client (for the auth mirror and sending) and on the server (for execution) with no further steps.
 */
object NeoForgeNetDiscovery {

    /** Load every discovered action set; returns how many were found and initialised. */
    fun discoverAndLoad(): Int {
        val classNames = ModList.get().allScanData
            .flatMap { it.getAnnotatedBy(BrassActionSet::class.java, ElementType.TYPE).toList() }
            .map { it.clazz().className }
            .distinct()
        return BrassActionSets.loadAll(classNames)
    }
}
