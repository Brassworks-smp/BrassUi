package net.swzo.brass.ui.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.swzo.brass.ui.demo.BrassGalleryScreen;
import net.swzo.brass.ui.kit.demo.BrassDemoCapture;
import net.swzo.brass.ui.kit.html.BrassHtmlEngine;
import net.swzo.brass.ui.kit.html.internal.UltralightHtmlEngine;
import net.swzo.brass.ui.kit.platform.BrassPlatform;
import net.swzo.brass.ui.neoforge.net.NeoForgeNetCommands;
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

        // The embedded-HTML widget seam. Ultralight's per-OS natives live under the game's config dir
        // and download on first use; on a machine that cannot load them the engine reports unavailable
        // and the widget shows its placeholder instead of breaking the UI.
        UltralightHtmlEngine.INSTANCE.configure(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve("ultralight").toFile());
        BrassHtmlEngine.Companion.bind(UltralightHtmlEngine.INSTANCE);

        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("brassui")
                // /brassui action <id> <json> — fire any registered action from chat, no UI needed.
                // Prints the translated outcome when the server replies.
                .then(Commands.literal("action")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .then(Commands.argument("json", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");
                                            String json = StringArgumentType.getString(ctx, "json");
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal(NeoForgeNetCommands.INSTANCE.sendAction(id, json)),
                                                    false);
                                            return 1;
                                        }))))
                .executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    // Defer to the next client tick so the closing chat screen doesn't clobber ours.
                    mc.execute(() -> mc.setScreen(new BrassGalleryScreen(new NeoForgeDemoHost())));
                    return 1;
                }));
    }
}
