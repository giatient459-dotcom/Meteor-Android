/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient;

import meteordevelopment.meteorclient.addons.AddonManager;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.meteor.KeyInputEvent;
import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.hud.screens.AddHudElementScreen;
import meteordevelopment.meteorclient.systems.hud.screens.HudEditorScreen;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.hud.screens.HudElementScreen;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.DiscordPresence;
import meteordevelopment.meteorclient.utils.PostInit;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.meteorclient.utils.ReflectInit;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Version;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.misc.input.KeyBinds;
import meteordevelopment.meteorclient.utils.network.OnlinePlayers;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import meteordevelopment.orbit.IEventBus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MeteorClient implements ClientModInitializer {
    public static final String MOD_ID = "meteor-client";
    public static final ModMetadata MOD_META;
    public static final String NAME;
    public static final Version VERSION;
    public static final String BUILD_NUMBER;

    public static MeteorClient INSTANCE;
    public static MeteorAddon ADDON;

    public static Minecraft mc;
    public static final IEventBus EVENT_BUS = new EventBus();

    // Small in-game shortcut intended for touch controls on launchers without a Right Shift button.
    private static final double TOUCH_GUI_BUTTON_X = 10;
    private static final double TOUCH_GUI_BUTTON_Y = 90;
    private static final double TOUCH_GUI_BUTTON_WIDTH = 66;
    private static final double TOUCH_GUI_BUTTON_HEIGHT = 30;
    private static final Color TOUCH_GUI_BUTTON_BACKGROUND = new Color(20, 20, 26, 220);
    private static final Color TOUCH_GUI_BUTTON_ACCENT = new Color(145, 61, 226);
    private static final Color TOUCH_GUI_BUTTON_TEXT = new Color(255, 255, 255);
    public static final File FOLDER = FabricLoader.getInstance().getGameDir().resolve(MOD_ID).toFile();
    public static final Logger LOG;

    static {
        MOD_META = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata();

        NAME = MOD_META.getName();
        LOG = LoggerFactory.getLogger(NAME);

        String versionString = MOD_META.getVersion().getFriendlyString();
        Matcher matcher = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)").matcher(versionString);
        // When building and running through IntelliJ and not Gradle it doesn't replace the version so just use a dummy
        versionString = !matcher.find() ? "0.0.0" : "%s.%s.%s".formatted(matcher.group(1), matcher.group(2), matcher.group(3));

        VERSION = new Version(versionString);
        BUILD_NUMBER = MOD_META.getCustomValue(MeteorClient.MOD_ID + ":build_number").getAsString();
    }

    @Override
    public void onInitializeClient() {
        if (INSTANCE == null) {
            INSTANCE = this;
            return;
        }

        // Global minecraft client accessor
        mc = Minecraft.getInstance();

        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            LOG.info("Force loading mixins");
            MixinEnvironment.getCurrentEnvironment().audit();
        }

        LOG.info("Initializing {}", NAME);

        // Pre-load
        if (!FOLDER.exists()) {
            FOLDER.getParentFile().mkdirs();
            FOLDER.mkdir();
            Systems.addPreLoadTask(() -> Modules.get().get(DiscordPresence.class).enable());
        }

        // Register addons
        AddonManager.init();

        // Register event handlers
        AddonManager.ADDONS.forEach(addon -> {
            try {
                EVENT_BUS.registerLambdaFactory(addon.getPackage(), (lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));
            } catch (AbstractMethodError e) {
                throw new RuntimeException("Addon \"%s\" is too old and cannot be ran.".formatted(addon.name), e);
            }
        });

        // Register init classes
        ReflectInit.registerPackages();

        // Pre init
        ReflectInit.init(PreInit.class);

        // Register module categories
        Categories.init();

        // Load systems
        Systems.init();

        // Subscribe after systems are loaded
        EVENT_BUS.subscribe(this);

        // Initialise addons
        AddonManager.ADDONS.forEach(MeteorAddon::onInitialize);

        // Sort modules after addons have added their own
        Modules.get().sortModules();

        // Load configs
        Systems.load();

        // Post init
        ReflectInit.init(PostInit.class);

        // Save on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            OnlinePlayers.leave();
            Systems.save();
            GuiThemes.save();
        }));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.gui.screen() == null && mc.gui.overlay() == null && KeyBinds.OPEN_COMMANDS.consumeClick()) {
            mc.gui.setScreen(new ChatScreen(Config.get().prefix.get(), true));
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!shouldShowTouchGuiButton()) return;

        HudRenderer renderer = HudRenderer.INSTANCE;
        renderer.begin(event.graphics);

        renderer.quad(TOUCH_GUI_BUTTON_X, TOUCH_GUI_BUTTON_Y, TOUCH_GUI_BUTTON_WIDTH, TOUCH_GUI_BUTTON_HEIGHT, TOUCH_GUI_BUTTON_BACKGROUND);
        renderer.quad(TOUCH_GUI_BUTTON_X, TOUCH_GUI_BUTTON_Y, TOUCH_GUI_BUTTON_WIDTH, 2, TOUCH_GUI_BUTTON_ACCENT);
        renderer.quad(TOUCH_GUI_BUTTON_X, TOUCH_GUI_BUTTON_Y + TOUCH_GUI_BUTTON_HEIGHT - 2, TOUCH_GUI_BUTTON_WIDTH, 2, TOUCH_GUI_BUTTON_ACCENT);
        renderer.quad(TOUCH_GUI_BUTTON_X, TOUCH_GUI_BUTTON_Y, 2, TOUCH_GUI_BUTTON_HEIGHT, TOUCH_GUI_BUTTON_ACCENT);
        renderer.quad(TOUCH_GUI_BUTTON_X + TOUCH_GUI_BUTTON_WIDTH - 2, TOUCH_GUI_BUTTON_Y, 2, TOUCH_GUI_BUTTON_HEIGHT, TOUCH_GUI_BUTTON_ACCENT);

        double textScale = 1.2;
        double textWidth = renderer.textWidth("GUI", false, textScale);
        double textHeight = renderer.textHeight(false, textScale);
        renderer.text(
            "GUI",
            TOUCH_GUI_BUTTON_X + (TOUCH_GUI_BUTTON_WIDTH - textWidth) / 2,
            TOUCH_GUI_BUTTON_Y + (TOUCH_GUI_BUTTON_HEIGHT - textHeight) / 2,
            TOUCH_GUI_BUTTON_TEXT,
            false,
            textScale
        );

        renderer.end();
    }

    private boolean shouldShowTouchGuiButton() {
        Config config = Config.get();
        return Utils.canUpdate()
            && mc.gui.screen() == null
            && mc.gui.overlay() == null
            && config != null
            && config.touchGuiButton.get();
    }

    public boolean handleTouchGuiButtonClick(double mouseX, double mouseY) {
        if (!shouldShowTouchGuiButton()) return false;

        boolean inside = mouseX >= TOUCH_GUI_BUTTON_X
            && mouseX <= TOUCH_GUI_BUTTON_X + TOUCH_GUI_BUTTON_WIDTH
            && mouseY >= TOUCH_GUI_BUTTON_Y
            && mouseY <= TOUCH_GUI_BUTTON_Y + TOUCH_GUI_BUTTON_HEIGHT;

        if (!inside) return false;

        toggleGui();
        return true;
    }

    @EventHandler
    private void onKey(KeyInputEvent event) {
        if (event.action == KeyAction.Press && KeyBinds.OPEN_GUI.matches(event.input)) {
            toggleGui();
        }
    }

    @EventHandler
    private void onMouseClick(MouseClickEvent event) {
        if (event.action == KeyAction.Press && KeyBinds.OPEN_GUI.matchesMouse(event.click)) {
            toggleGui();
        }
    }

    private void toggleGui() {
        if (Utils.canCloseGui()) mc.gui.screen().onClose();
        else if (Utils.canOpenGui()) Tabs.get().getFirst().openScreen(GuiThemes.get());
    }

    // Hide HUD

    private boolean wasWidgetScreen, wasHudHiddenRoot;

    @EventHandler(priority = EventPriority.LOWEST)
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen instanceof WidgetScreen) {
            if (!wasWidgetScreen) wasHudHiddenRoot = mc.gameRenderer.gameRenderState().guiRenderState.isHudHidden;
            if (GuiThemes.get().hideHUD() || wasHudHiddenRoot) {
                // Always show the MC HUD in the HUD editor screen since people like
                // to align some items with the hotbar or chat
                mc.gameRenderer.gameRenderState().guiRenderState.isHudHidden = !(event.screen instanceof HudEditorScreen)
                    && !(event.screen instanceof AddHudElementScreen)
                    && !(event.screen instanceof HudElementScreen);
            }
        } else {
            if (wasWidgetScreen) mc.gameRenderer.gameRenderState().guiRenderState.isHudHidden = wasHudHiddenRoot;
            wasHudHiddenRoot = mc.gameRenderer.gameRenderState().guiRenderState.isHudHidden;
        }

        wasWidgetScreen = event.screen instanceof WidgetScreen;
    }

    public static Identifier identifier(String path) {
        return Identifier.fromNamespaceAndPath(MeteorClient.MOD_ID, path);
    }
}
