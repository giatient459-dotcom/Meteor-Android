/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class GuiCommand extends Command {
    public GuiCommand() {
        super("gui", "Opens the Meteor GUI without a keyboard keybind.", "clickgui");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(_ -> {
            if (Utils.canCloseGui()) {
                mc.gui.screen().onClose();
                return SINGLE_SUCCESS;
            }

            // Chat is still the active screen while the command is being dispatched.
            // Queue the GUI so Utils can open it after the chat screen has closed.
            if (Utils.screenToOpen == null) {
                Tab tab = Tabs.get().getFirst();
                TabScreen screen = tab.createScreen(GuiThemes.get());
                screen.addDirect(GuiThemes.get().topBar()).top().centerX();
                Utils.screenToOpen = screen;
            }

            return SINGLE_SUCCESS;
        });
    }
}
