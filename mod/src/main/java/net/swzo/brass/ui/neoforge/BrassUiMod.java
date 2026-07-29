package net.swzo.brass.ui.neoforge;

import net.neoforged.fml.common.Mod;

/**
 * The {@code brassui} mod entry point — deliberately empty.
 *
 * The mod carries the Elementa UI toolkit (folded into this jar) plus the {@code /brassui} showcase,
 * and bundles Elementa + UniversalCraft as jar-in-jar game libraries. Everything it actually does is
 * wired through {@link BrassUiClientCommands}, which is {@code @EventBusSubscriber(Dist.CLIENT)} — so a
 * dedicated server loads only this no-op class and never touches a Kotlin class, which is what keeps the
 * Kotlin-For-Forge dependency client-only.
 *
 * A {@code @Mod} class is Java (not Kotlin) for exactly that reason: it is the one type here that loads
 * on both sides, so it must not drag the Kotlin runtime onto a server.
 */
@Mod("brassui")
public final class BrassUiMod {
    public BrassUiMod() {
    }
}
