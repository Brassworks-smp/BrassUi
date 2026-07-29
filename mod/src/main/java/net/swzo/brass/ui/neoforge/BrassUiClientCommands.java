package net.swzo.brass.ui.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.swzo.brass.ui.demo.BrassGalleryScreen;
import net.swzo.brass.ui.kit.demo.BrassDemoCapture;
import net.swzo.brass.ui.kit.platform.BrassPlatform;
import net.swzo.brass.ui.neoforge.shot.NeoForgeDemoCapture;
import net.swzo.brass.ui.kit.text.BrassSyntax;

/**
 * Client-only {@code /brassui} command that opens the {@link BrassGalleryScreen} — the widget gallery,
 * shared with the standalone desktop app and hosted here by {@link NeoForgeDemoHost}. Client-side
 * (never sent to the server) and unrestricted: it just pops the gallery open. Registration rides the
 * game event bus.
 *
 * <p>This used to carry a {@code shot} subcommand that swept every widget across every theme and wrote
 * the whole set to disk, plus a companion system property that did the same off the title screen. Both
 * are gone. Capturing is now manual and one widget at a time, in the demo browser reached from the
 * gallery's Demos section — which is how these assets were actually being produced anyway: a sweep
 * regenerated hundreds of files to get at the two that had changed, and the theme dimension multiplied
 * that again for output nobody was looking at.
 */
@EventBusSubscriber(modid = "brassui", value = Dist.CLIENT)
public final class BrassUiClientCommands {

    private BrassUiClientCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        // Syntax rules for anything drawing markdown, and the platform seam for game content (item and
        // entity rendering). Until this runs the toolkit still works, just without them.
        BrassSyntax.INSTANCE.init();
        BrassPlatform.Companion.bind(NeoForgePlatform.INSTANCE);
        // The demo browser's shutter and record button. Bound here rather than at mod construction so
        // it sits beside the platform seam it is a sibling of; unbound, the browser still previews.
        BrassDemoCapture.Companion.bind(NeoForgeDemoCapture.INSTANCE);

        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("brassui")
                .executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    // Defer to the next client tick so the closing chat screen doesn't clobber ours.
                    mc.execute(() -> mc.setScreen(new BrassGalleryScreen(new NeoForgeDemoHost())));
                    return 1;
                }));
    }
}
