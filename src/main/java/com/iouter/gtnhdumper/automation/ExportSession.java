package com.iouter.gtnhdumper.automation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import com.google.gson.JsonObject;
import com.iouter.gtnhdumper.CommonProxy;
import com.iouter.gtnhdumper.GTNHDumper;
import com.iouter.gtnhdumper.Tags;
import com.iouter.gtnhdumper.common.base.WikiDumper;
import com.iouter.gtnhdumper.common.dumper.AdvItemPanelDumper;
import com.iouter.gtnhdumper.common.dumper.FluidsDumper;
import com.iouter.gtnhdumper.common.dumper.GTMaterialDumper;
import com.iouter.gtnhdumper.common.dumper.GTNHDimensionDumper;
import com.iouter.gtnhdumper.common.dumper.GTOreVeinDumper;
import com.iouter.gtnhdumper.common.dumper.GTSmallOreVeinDumper;
import com.iouter.gtnhdumper.common.dumper.GTStructuresDumper;
import com.iouter.gtnhdumper.common.dumper.GTUndergroundFluidDumper;
import com.iouter.gtnhdumper.common.dumper.ItemIconDumper;
import com.iouter.gtnhdumper.common.dumper.OreDictionaryDumper;
import com.iouter.gtnhdumper.common.dumper.QuestDumper;
import com.iouter.gtnhdumper.common.dumper.QuestLineDumper;
import com.iouter.gtnhdumper.common.dumper.RecipesDumper;
import com.iouter.gtnhdumper.common.dumper.TC4ResearchDumper;
import com.iouter.gtnhdumper.common.dumper.TICMaterialDumper;
import com.iouter.gtnhdumper.common.utils.RecipeCoverage;
import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;

import codechicken.nei.ItemList;
import codechicken.nei.recipe.GuiUsageRecipe;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategoryList;
import thaumcraft.common.Thaumcraft;

/** One fresh world and one export per process. Game state stays on its owning thread. */
public final class ExportSession {

    private static ExportSession active;
    private volatile boolean exporting;
    private final File runDirectory;
    private final File output;
    private final String runId;
    private final boolean smoke;
    private final boolean managedByRunner;
    private final boolean keepWorld;
    private final boolean exitGame;
    private final Path worldDirectory;
    private boolean worldCreated;
    private volatile boolean closingWorld;
    private volatile boolean lifecycleComplete;
    private final boolean layoutsOnly;
    private final boolean imagesOnly;
    private final boolean imageDiagnostics;
    private final Set<String> imageKeys = new LinkedHashSet<>();
    private ItemIconDumper pendingIcons;
    private boolean iconsReady;
    private Throwable iconFailure;
    private int iconErrors;
    private long iconStarted;
    private final long seed;
    private final File imageMetadata;
    private final File imageResume;
    private final List<String> researchKeys = new ArrayList<>();
    private final List<String> completed = new ArrayList<>();
    private final List<String> failed = new ArrayList<>();
    private final List<ExportTask> tasks = new ArrayList<>();
    private final Map<String, Long> taskMillis = new LinkedHashMap<>();
    private final AtomicInteger loggedErrors = new AtomicInteger();
    private final long deadline = System.currentTimeMillis() + 7200000L;
    private volatile boolean prepared;
    private volatile Throwable preparationError;
    private boolean preparationStarted;
    private volatile String stage = "WAIT_MENU";
    private int nextTask;
    private int readyTicks;
    private long nextHeartbeat;
    private volatile boolean finished;
    private final AtomicBoolean exitScheduled = new AtomicBoolean();
    private ClientSettings originalSettings;

    private ExportSession(JsonObject request, File gameDirectory) throws IOException {
        runId = request.get("runId")
            .getAsString();
        if (!runId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,100}")) {
            throw new IllegalArgumentException("Invalid export run ID");
        }
        managedByRunner = request.has("managedByRunner") && request.get("managedByRunner")
            .getAsBoolean();
        keepWorld = request.has("keepWorld") && request.get("keepWorld")
            .getAsBoolean();
        exitGame = !request.has("exitGame") || request.get("exitGame")
            .getAsBoolean();
        worldDirectory = gameDirectory.toPath()
            .toRealPath()
            .resolve("saves")
            .resolve("Dumper-" + runId);
        smoke = request.has("smoke") && request.get("smoke")
            .getAsBoolean();
        layoutsOnly = request.has("layoutsOnly") && request.get("layoutsOnly")
            .getAsBoolean();
        imageDiagnostics = request.has("imageDiagnostics") && request.get("imageDiagnostics")
            .getAsBoolean();
        imagesOnly = request.has("imagesOnly") && request.get("imagesOnly")
            .getAsBoolean();
        if (imagesOnly && (layoutsOnly || smoke))
            throw new IllegalArgumentException("imagesOnly excludes layoutsOnly/smoke");
        if (request.has("imageKeys")) {
            for (com.google.gson.JsonElement key : request.getAsJsonArray("imageKeys")) {
                String value = key.getAsString();
                if (value.isEmpty()) throw new IllegalArgumentException("Empty image key");
                imageKeys.add(value);
            }
            if (imageKeys.isEmpty()) throw new IllegalArgumentException("imageKeys must not be empty");
        }
        seed = request.has("seed") ? request.get("seed")
            .getAsLong() : 290002L;
        imageMetadata = request.has("imageMetadata") ? new File(
            request.get("imageMetadata")
                .getAsString())
            : null;
        if (imageMetadata != null && !imageMetadata.isFile())
            throw new IllegalArgumentException("Reference image metadata is missing");
        imageResume = request.has("imageResume") ? new File(
            request.get("imageResume")
                .getAsString())
            : null;
        runDirectory = new File(gameDirectory, "dumper-runs/" + runId);
        if (new File(runDirectory, "status.json").exists()) {
            throw new IllegalArgumentException("Export run ID already used");
        }
        output = new File(runDirectory, "dumps");
        Files.createDirectories(output.toPath());
        Files.write(
            new File(runDirectory, "request.json").toPath(),
            GTNHDumper.GSON.toJson(request)
                .getBytes(StandardCharsets.UTF_8));
        status("WAIT_MENU", "Waiting for main menu");
    }

    public static void register() {
        File game = Minecraft.getMinecraft().mcDataDir;
        File requestFile = new File(game, "config/gtnhdumper-autorun.json");
        try {
            JsonObject request = readRequest(requestFile.toPath(), GTNHDumper.GSON);
            if (request == null || !request.has("enabled")
                || !request.get("enabled")
                    .getAsBoolean())
                return;
            // Consume before any export side effects; a crash must not arm the next launch.
            request.addProperty("enabled", false);
            JsonObject consumed = request.deepCopy();
            consumed.remove("runId");
            consumed.remove("managedByRunner");
            consumed.remove("imageMetadata");
            consumed.remove("imageResume");
            File temporary = new File(requestFile.getParentFile(), "gtnhdumper-autorun.tmp");
            Files.write(
                temporary.toPath(),
                GTNHDumper.GSON.toJson(consumed)
                    .getBytes(StandardCharsets.UTF_8));
            Files.move(temporary.toPath(), requestFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (!request.has("runId")) {
                request.addProperty(
                    "runId",
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                        .withZone(java.time.ZoneOffset.UTC)
                        .format(java.time.Instant.now()) + "-"
                        + java.util.UUID.randomUUID()
                            .toString()
                            .substring(0, 8));
            }
            active = new ExportSession(request, game);
            FMLCommonHandler.instance()
                .bus()
                .register(active);
            AbstractAppender appender = new AbstractAppender("DumperExportErrors", null, null) {

                @Override
                public void append(LogEvent event) {
                    if ((active.exporting && Tags.MODID.equals(event.getLoggerName()) || active.closingWorld)
                        && (event.getLevel() == Level.ERROR || event.getLevel() == Level.FATAL)) {
                        active.loggedErrors.incrementAndGet();
                    }
                }
            };
            appender.start();
            ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).addAppender(appender);
        } catch (Exception error) {
            active = null;
            GTNHDumper.LOG.error("Cannot initialize automated export", error);
        }
    }

    /** Create an editable, disabled request on first launch; preserve an existing request verbatim. */
    static JsonObject readRequest(Path path, com.google.gson.Gson gson) throws IOException {
        if (!Files.exists(path)) {
            JsonObject defaults = new JsonObject();
            defaults.addProperty("enabled", false);
            defaults.addProperty("keepWorld", false);
            defaults.addProperty("exitGame", true);
            defaults.addProperty("seed", 290002L);
            defaults.addProperty("smoke", false);
            defaults.addProperty("layoutsOnly", false);
            defaults.addProperty("imagesOnly", false);
            defaults.addProperty("imageDiagnostics", false);
            // Omitted image paths/keys select a full export; runId is generated for each armed launch.
            Files.createDirectories(path.getParent());
            Files.write(
                path,
                gson.toJson(defaults)
                    .getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE_NEW);
            return defaults;
        }
        return gson.fromJson(new String(Files.readAllBytes(path), StandardCharsets.UTF_8), JsonObject.class);
    }

    public static boolean isExporting() {
        return active != null && active.exporting;
    }

    public static File outputDirectory() {
        return active.output;
    }

    public static boolean imageDiagnostics() {
        return isExporting() && active.imageDiagnostics;
    }

    public static Set<String> imageKeys() {
        return isExporting() ? Collections.unmodifiableSet(active.imageKeys) : Collections.emptySet();
    }

    public static File imageMetadata() {
        return isExporting() ? active.imageMetadata : null;
    }

    public static File imageResume() {
        return isExporting() ? active.imageResume : null;
    }

    public static void progress(String message) {
        if (active == null || !active.exporting) return;
        try {
            active.status("EXPORTING", message);
        } catch (IOException error) {
            throw new IllegalStateException("Could not write export progress", error);
        }
    }

    public static void serverStopped() {
        if (active == null) return;
        if (!active.finished && !active.stage.equals("WAIT_MENU")) {
            active.preparationError = new IllegalStateException(
                "Integrated server stopped before export completion; see FML log");
        }
    }

    private void scheduleExit() {
        if (exitGame && finished && lifecycleComplete && exitScheduled.compareAndSet(false, true)) {
            // Some mod workers outlive Minecraft's main loop. Exit only after the server has saved and stopped.
            Thread exit = new Thread(() -> {
                try {
                    Thread.sleep(5000L);
                    FMLCommonHandler.instance()
                        .exitJava(failed.isEmpty() ? 0 : 1, false);
                } catch (InterruptedException error) {
                    Thread.currentThread()
                        .interrupt();
                }
            }, "DumperAutoExit");
            exit.setDaemon(true);
            exit.start();
        }
    }

    /** Server-side cheat book use preserves Thaumcraft's normal research completion behavior. */
    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished || preparationStarted || !stage.equals("WAIT_WORLD")) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager().playerEntityList.isEmpty()) return;
        preparationStarted = true;
        try {
            EntityPlayerMP player = (EntityPlayerMP) server.getConfigurationManager().playerEntityList.get(0);
            player.setGameType(WorldSettings.GameType.CREATIVE);
            server.getCommandManager()
                .executeCommand(server, "gamerule doMobSpawning false");
            server.getCommandManager()
                .executeCommand(server, "gamerule doDaylightCycle false");
            if (CommonProxy.isTCLoaded) {
                Item book = GameRegistry.findItem("Thaumcraft", "ItemThaumonomicon");
                if (book == null) throw new IllegalStateException("Thaumcraft cheat book missing");
                book.onItemRightClick(new ItemStack(book, 1, 42), player.worldObj, player);
                for (ResearchCategoryList category : ResearchCategories.researchCategories.values()) {
                    researchKeys.addAll(category.research.keySet());
                }
                String name = player.getCommandSenderName();
                for (String suffix : new String[] { "", " PERM", " TEMP" }) {
                    int result = server.getCommandManager()
                        .executeCommand(server, "tc warp " + name + " set 0" + suffix);
                    if (result <= 0) throw new IllegalStateException("Thaumcraft warp command failed: " + suffix);
                }
                if (Thaumcraft.proxy.getPlayerKnowledge()
                    .getWarpTotal(name) != 0) {
                    throw new IllegalStateException("Thaumcraft warp was not cleared");
                }
                for (String key : researchKeys) {
                    if (!ThaumcraftApiHelper.isResearchComplete(name, key)) {
                        throw new IllegalStateException("Cheat book did not complete research: " + key);
                    }
                }
            }
            prepared = true;
        } catch (Exception | LinkageError error) {
            preparationError = error;
        }
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (System.currentTimeMillis() > deadline) throw new IllegalStateException("Export timed out");
            if (preparationError != null) throw new IllegalStateException(
                prepared ? "Export world became unavailable" : "World preparation failed",
                preparationError);
            if (stage.equals("WAIT_MENU")) {
                if (!(mc.currentScreen instanceof GuiMainMenu)
                    && (mc.currentScreen == null || !mc.currentScreen.getClass()
                        .getName()
                        .equals("lumien.custommainmenu.gui.GuiCustom")))
                    return;
                originalSettings = new ClientSettings(mc.gameSettings);
                mc.gameSettings.pauseOnLostFocus = false;
                String world = "Dumper-" + runId;
                Files.createDirectories(worldDirectory.getParent());
                if (!worldDirectory.getParent()
                    .toRealPath()
                    .equals(worldDirectory.getParent()))
                    throw new IOException("Saves directory redirects outside its expected location");
                // Exclusive creation proves ownership; an existing world is never opened or removed.
                Files.createDirectory(worldDirectory);
                worldCreated = true;
                mc.gameSettings.difficulty = EnumDifficulty.PEACEFUL;
                status("WAIT_WORLD", "Creating creative flat world");
                mc.launchIntegratedServer(
                    world,
                    world,
                    new WorldSettings(seed, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT)
                        .enableCommands());
                return;
            }
            if (stage.equals("WAIT_WORLD") && System.currentTimeMillis() >= nextHeartbeat) {
                nextHeartbeat = System.currentTimeMillis() + 10000L;
                status(
                    "WAIT_WORLD",
                    "world=" + (mc.theWorld != null)
                        + ", player="
                        + (mc.thePlayer != null)
                        + ", prepared="
                        + prepared
                        + ", NEI="
                        + ItemList.loadFinished);
            }
            if (mc.theWorld == null || mc.thePlayer == null) return;
            // Closing the cheat-book GUI also keeps the integrated server ticking while readiness is checked.
            if (mc.currentScreen != null) mc.displayGuiScreen(null);
            if (!prepared || !ItemList.loadFinished || GuiUsageRecipe.usagehandlers.isEmpty()) return;
            if (stage.equals("WAIT_WORLD")) {
                for (String key : researchKeys) {
                    if (!ThaumcraftApiHelper.isResearchComplete(mc.thePlayer.getCommandSenderName(), key)) return;
                }
                // Allow pending client packets/render initialization to settle after explicit readiness checks.
                if (++readyTicks < 40) return;
                planTasks();
                status("EXPORTING", "World prepared; NEI and research are ready");
            }
            if (nextTask < tasks.size()) {
                ExportTask task = tasks.get(nextTask);
                if (task.icons != null && !iconsReady) {
                    if (pendingIcons == null) {
                        exporting = true;
                        pendingIcons = task.icons;
                        iconErrors = loggedErrors.get();
                        iconStarted = System.nanoTime();
                        status("EXPORTING", "Sampling timed item icons");
                        pendingIcons.prepareTimedIconsAsync(() -> iconsReady = true, error -> {
                            iconFailure = error;
                            iconsReady = true;
                        });
                    }
                    return;
                }
                nextTask++;
                status("EXPORTING", task.name);
                int before = task.icons == null ? loggedErrors.get() : iconErrors;
                long started = task.icons == null ? System.nanoTime() : iconStarted;
                exporting = true;
                try {
                    if (iconFailure != null) throw new IllegalStateException("Icon sampling failed", iconFailure);
                    task.action.run();
                    if (loggedErrors.get() != before)
                        throw new IllegalStateException("Dumper logged errors in " + task.name);
                    completed.add(task.name);
                } catch (Exception | LinkageError error) {
                    // Tasks write separate outputs. Keep later diagnostics/material dumps, but fail the run overall.
                    failed.add("Automated export task failed: " + task.name + ": " + error);
                    GTNHDumper.LOG.error("Automated export task failed: " + task.name, error);
                } finally {
                    if (pendingIcons != null) pendingIcons.cancelCapture();
                    pendingIcons = null;
                    iconsReady = false;
                    iconFailure = null;
                    exporting = false;
                    taskMillis.put(task.name, (System.nanoTime() - started) / 1000000L);
                }
                status("EXPORTING", (completed.contains(task.name) ? "Finished " : "Failed ") + task.name);
                return;
            }
            finish(mc);
        } catch (Exception | LinkageError error) {
            failed.add(error.toString());
            GTNHDumper.LOG.error("Automated export failed", error);
            try {
                status("FAILED", error.toString());
            } catch (IOException writeError) {
                GTNHDumper.LOG.error("Could not write export failure status", writeError);
            }
            finish(mc);
        }
    }

    private void finish(Minecraft mc) {
        if (finished) return;
        finished = true;
        exporting = false;
        try {
            if (pendingIcons != null) pendingIcons.cancelCapture();
            closingWorld = true;
            int before = loggedErrors.get();
            // loadWorld waits for the integrated server to save and stop, then flushes the save cache.
            mc.loadWorld(null);
            closingWorld = false;
            lifecycleComplete = true;
            if (loggedErrors.get() != before)
                throw new IOException("Errors while saving/stopping world; world retained");
            if (!managedByRunner && failed.isEmpty() && !keepWorld && worldCreated) {
                deleteOwnedWorld(worldDirectory);
            }
            status(
                failed.isEmpty() ? "EXPORTED" : "FAILED",
                managedByRunner ? "World stopped; external validation required"
                    : "World stopped; export lifecycle finished");
        } catch (Exception | LinkageError error) {
            failed.add(error.toString());
            GTNHDumper.LOG.error("Could not finish automated export; remaining world retained", error);
            try {
                status("FAILED", error.toString());
            } catch (IOException writeError) {
                GTNHDumper.LOG.error("Could not write export failure status", writeError);
            }
        } finally {
            closingWorld = false;
            if (originalSettings != null) originalSettings.restore(mc.gameSettings);
        }
        if (exitGame) {
            mc.shutdown();
            scheduleExit();
        } else if (lifecycleComplete) {
            mc.displayGuiScreen(new GuiMainMenu());
        }
    }

    /** Export-only settings are restored after world shutdown, including failures during preparation or capture. */
    static final class ClientSettings {

        private final boolean pauseOnLostFocus;
        private final EnumDifficulty difficulty;

        ClientSettings(GameSettings settings) {
            pauseOnLostFocus = settings.pauseOnLostFocus;
            difficulty = settings.difficulty;
        }

        void restore(GameSettings settings) {
            settings.pauseOnLostFocus = pauseOnLostFocus;
            settings.difficulty = difficulty;
            // World startup or other UI actions may have persisted the temporary values to options.txt.
            settings.saveOptions();
        }
    }

    /** Preflight redirected paths before deleting the directory exclusively created by this session. */
    static void deleteOwnedWorld(Path world) throws IOException {
        if (!world.toRealPath()
            .equals(world)
            || !world.getParent()
                .toRealPath()
                .equals(world.getParent()))
            throw new IOException("World directory redirects outside its expected location");
        Files.walkFileTree(world, new SimpleFileVisitor<Path>() {

            private void check(Path path, BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink() || attributes.isOther()
                    || !path.toRealPath()
                        .equals(path))
                    throw new IOException("World contains a redirected or special path: " + path);
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                check(dir, attributes);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                check(file, attributes);
                return FileVisitResult.CONTINUE;
            }
        });
        Files.walkFileTree(world, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void add(String name, WikiDumper dumper, int mode) {
        ExportTask task = new ExportTask(name, () -> dumper.dumpTo(new File(output, name + ".json"), mode));
        if (dumper instanceof ItemIconDumper) task.icons = (ItemIconDumper) dumper;
        tasks.add(task);
    }

    private void planTasks() {
        if (layoutsOnly) {
            tasks.add(
                new ExportTask(
                    "nei-layouts",
                    () -> com.iouter.gtnhdumper.common.dumper.NEILayoutDumper.dump(new File(output, "nei-layouts"))));
            return;
        }
        if (smoke) {
            add("ticmaterial", new TICMaterialDumper(), 1);
            return;
        }
        // Research synchronization makes gated recipes visible; successful scans stay cached until restart.
        tasks.add(new ExportTask("item-variants", () -> {
            RecipeItemVariants.get();
            Files.write(
                new File(runDirectory, "recipe-scan-failures.json").toPath(),
                GTNHDumper.GSON.toJson(RecipeItemVariants.failureDetails())
                    .getBytes(StandardCharsets.UTF_8));
            if (!RecipeItemVariants.isComplete()) throw new IllegalStateException("Recipe item scan incomplete");
        }));
        add("itemicon", new ItemIconDumper(), 0);
        if (imagesOnly) return;
        add("advitempanel", new AdvItemPanelDumper(), 0);
        tasks.add(new ExportTask("recipes", () -> new RecipesDumper().dump(0)));
        add("oreDictionary", new OreDictionaryDumper(), 0);
        add("fluid", new FluidsDumper(), 0);
        if (CommonProxy.isGTLoaded) {
            add("gtmaterial", new GTMaterialDumper(), 1);
            add("gtorevein", new GTOreVeinDumper(), 0);
            add("gtsmallorevein", new GTSmallOreVeinDumper(), 0);
            add("gtundergroundfluid", new GTUndergroundFluidDumper(), 0);
            add("gtnhdimension", new GTNHDimensionDumper(), 0);
            if (CommonProxy.isBR6343Loaded)
                tasks.add(new ExportTask("structures", () -> new GTStructuresDumper().dumpFile()));
        }
        if (CommonProxy.isTiCLoaded) add("ticmaterial", new TICMaterialDumper(), 1);
        if (CommonProxy.isTCLoaded) add("tc4research", new TC4ResearchDumper(), 0);
        if (CommonProxy.isBQLoaded) {
            add("quest", new QuestDumper(), 0);
            add("questline", new QuestLineDumper(), 0);
        }
    }

    private void status(String newStage, String message) throws IOException {
        stage = newStage;
        JsonObject result = new JsonObject();
        result.addProperty("runId", runId);
        result.addProperty("stage", stage);
        result.addProperty("message", message);
        result.addProperty("updatedMillis", System.currentTimeMillis());
        result.addProperty("smoke", smoke);
        result.addProperty("layoutsOnly", layoutsOnly);
        // Requested reuse is not a count or proof of complete image coverage.
        result.addProperty("imagesReused", imageMetadata != null || imageResume != null);
        result.addProperty("keepWorld", keepWorld);
        result.addProperty("exitGame", exitGame);
        result.addProperty("managedByRunner", managedByRunner);
        result.addProperty("seed", seed);
        result.addProperty("language", Minecraft.getMinecraft().gameSettings.language);
        result.addProperty("researchCount", researchKeys.size());
        result.addProperty("handlerCount", GuiUsageRecipe.usagehandlers.size());
        result.add("completed", GTNHDumper.GSON.toJsonTree(completed));
        result.add("taskMillis", GTNHDumper.GSON.toJsonTree(taskMillis));
        result.add("failed", GTNHDumper.GSON.toJsonTree(failed));
        result.add("unsupportedRecipeSources", GTNHDumper.GSON.toJsonTree(RecipeCoverage.unsupported()));
        File temporary = new File(runDirectory, "status.tmp");
        Files.write(
            temporary.toPath(),
            GTNHDumper.GSON.toJson(result)
                .getBytes(StandardCharsets.UTF_8));
        // Windows readers may briefly hold the destination without delete sharing.
        for (int attempt = 0;; attempt++) {
            try {
                Files.move(
                    temporary.toPath(),
                    new File(runDirectory, "status.json").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
                break;
            } catch (java.nio.file.FileSystemException error) {
                if (attempt >= 39) throw error;
                try {
                    Thread.sleep(50);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread()
                        .interrupt();
                    throw new IOException("Interrupted while publishing export status", interrupted);
                }
            }
        }
        GTNHDumper.info("Export " + runId + " " + stage + ": " + message);
    }

    @FunctionalInterface
    private interface ExportAction {

        void run() throws Exception;
    }

    private static final class ExportTask {

        private final String name;
        private final ExportAction action;
        private ItemIconDumper icons;

        private ExportTask(String name, ExportAction action) {
            this.name = name;
            this.action = action;
        }
    }
}
