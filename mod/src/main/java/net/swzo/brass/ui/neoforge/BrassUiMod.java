package net.swzo.brass.ui.neoforge;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code brassui} mod entry point — deliberately empty.
 *
 * The mod carries the Elementa UI toolkit (folded into this jar) plus the {@code /brassui} showcase,
 * bundles Elementa + UniversalCraft as jar-in-jar game libraries, and self-wires the networking module:
 * {@code BrassNetNeoForgeEvents} (Kotlin) registers the payloads and discovers action sets on BOTH
 * sides, so a single jar serves a client and a dedicated server. Client-only presentation (the
 * {@code /brassui} gallery, the platform seam) rides {@link BrassUiClientCommands}, which stays
 * {@code @EventBusSubscriber(Dist.CLIENT)}.
 *
 * A {@code @Mod} class is Java (not Kotlin) because it is the one type here that must load before the
 * Kotlin runtime is available; the Kotlin classes follow once Kotlin-For-Forge has loaded (declared
 * {@code side = "BOTH"} in the mods.toml).
 */
@Mod("brassui")
public final class BrassUiMod {
    private static final Logger LOGGER = LoggerFactory.getLogger("brassui");

    public BrassUiMod() {
        // The CI boot smoke test greps the log for this line (scripts/smoke-boot.sh) — it is the
        // deterministic "the mod actually loaded" marker on both client and dedicated server.
        LOGGER.info("BrassUi loaded");
    }
}
