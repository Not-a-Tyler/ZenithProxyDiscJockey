package tyler.discjockey.module;

import com.github.rfresh2.EventConsumer;
import com.zenith.Proxy;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandSource;
import com.zenith.discord.Embed;
import com.zenith.event.chat.WhisperChatEvent;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.player.Input;
import com.zenith.feature.player.InputRequest;
import com.zenith.feature.player.RotationHelper;
import com.zenith.feature.player.World;
import com.zenith.mc.block.*;
import com.zenith.mc.block.properties.NoteBlockInstrument;
import com.zenith.mc.block.properties.api.BlockStateProperties;
import com.zenith.module.api.Module;
import com.zenith.util.ChatUtil;
import com.zenith.util.struct.Pair;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import tyler.discjockey.DiscJockeyPlugin;
import tyler.discjockey.utils.Note;
import tyler.discjockey.utils.Song;
import tyler.discjockey.utils.SongLoader;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.*;
import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;
import static tyler.discjockey.DiscJockeyPlugin.LOG;
import static tyler.discjockey.DiscJockeyPlugin.PLUGIN_CONFIG;

public class DiscJockeyModule extends Module {
    public boolean running;
    public Song song;

    private int index;
    private double tick; // Aka song position
    private HashMap<NoteBlockInstrument, HashMap<Byte, BlockPos>> noteBlocks = null;
    public boolean tuned;
    private long lastPlaybackTickAt = -1L;

    // The thread executing the tickPlayback method
    private Thread playbackThread = null;
    public long playbackLoopDelay = 5;
    // Just for external debugging purposes
    public HashMap<Block, Integer> missingInstrumentBlocks = new HashMap<>();

    private long lastInteractAt = -1;
    private float availableInteracts = 8;
    private int tuneInitialUntunedBlocks = -1;
    private HashMap<BlockPos, Pair<Integer, Long>> notePredictions = new HashMap<>();
    private boolean tempPaused = false;
    private boolean inQueue = false;
    private int ticksUntillJump = 0;

    @Override
    public boolean enabledSetting() {
        return DiscJockeyPlugin.PLUGIN_CONFIG.discJockey.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ClientBotTick.class, this::handleBotTick),
                of(WhisperChatEvent.class, this::onWhisper)
        );
    }

    public void onWhisper(WhisperChatEvent event) {
        String message = event.message().trim();
        boolean inRenderDistance = CACHE.getEntityCache().getEntities().values().stream().anyMatch(entity -> entity.getEntityType() == EntityType.PLAYER && entity.getUuid().equals(event.sender().getProfileId()));

        if (inRenderDistance && PLUGIN_CONFIG.discJockey.chatControl) {
            if (!message.startsWith(PLUGIN_CONFIG.discJockey.commandPrefix + "dj")) return;
            message = message.substring(PLUGIN_CONFIG.discJockey.commandPrefix.length());

            CommandContext commandContext = CommandContext.create(message, DiscJockeyCommandSource.INSTANCE);

            commandContext.getData().put("DiscJockeyPlugin", event.sender());

            COMMAND.execute(commandContext);
            String response;
            if (commandContext.getEmbed().isTitlePresent()) {
                response = ChatUtil.sanitizeChatMessage(commandContext.getEmbed().title());
            } else {
                response = "Command executed";
            }

            reply(event.sender(), response);
        }
    }

    public static class DiscJockeyCommandSource implements CommandSource {
        public static final DiscJockeyCommandSource INSTANCE = new DiscJockeyCommandSource();
        @Override
        public String name() {
            return "Disc Jockey";
        }

        @Override
        public boolean validateAccountOwner(final CommandContext ctx) {
            return false;
        }

        @Override
        public void logEmbed(CommandContext ctx, Embed embed) {

        }
    }

    public static void reply(PlayerListEntry sender, String text) {
        String reply = "msg " + sender.getName() + " " + text + " " + UUID.randomUUID().toString().replace("-", "");
        LOG.info("sending reply " + reply);
        Proxy.getInstance().getClient().getChannel().writeAndFlush(new ServerboundChatCommandPacket(reply));
    }

    public @NotNull HashMap<NoteBlockInstrument, @Nullable NoteBlockInstrument> instrumentMap = new HashMap<>(); // Toy

    public synchronized void startPlaybackThread() {
        this.playbackThread = new Thread(() -> {
            Thread ownThread = this.playbackThread;
            while(ownThread == this.playbackThread) {
                try {
                    // Accuracy doesn't really matter at this precision imo
                    Thread.sleep(playbackLoopDelay);
                }catch (Exception ex) {
                    ex.printStackTrace();
                }
                tickPlayback();
            }
        });
        this.playbackThread.start();
    }

    public synchronized void stopPlaybackThread() {
        this.playbackThread = null; // Should stop on its own then
    }

    @Override
    public void onDisable() {
        stop();
    }


    public boolean pause() {
        stopPlaybackThread();
        running = false;
        return true;
    }

    public boolean resume() {
        if (running) {
            LOG.warn("DiscJockeyModule is already running, cannot resume");
            return false;
        }
        if (song == null) {
            LOG.warn("DiscJockeyModule has no song to resume");
            return false;
        }
        startPlaybackThread();
        running = true;
        tempPaused = false;
        return true;
    }

    public void playRandomSong() {
        Song randomSong = SongLoader.SONGS.get(new Random().nextInt(SongLoader.SONGS.size()));
        if (randomSong != null) {
            start(randomSong);
        } else {
            LOG.error("No songs available to play");
        }
    }

    public synchronized void start(Song song) {
        if (CACHE.getPlayerCache().getGameMode() != GameMode.SURVIVAL) {
            LOG.error("not in survival mode, cannot play song");
            return;
        }

        stop();
        this.song = song;
        if (this.playbackThread == null) startPlaybackThread();
        running = true;
        lastPlaybackTickAt = System.currentTimeMillis();
        missingInstrumentBlocks.clear();
        LOG.info("Starting song " + song.displayName + " (" + song.getLengthInSeconds() + "s)");
    }

    public synchronized void stop() {
        stopPlaybackThread();
        song = null;
        running = false;
        index = 0;
        tick = 0;
        noteBlocks = null;
        notePredictions.clear();
        tuned = false;
        tuneInitialUntunedBlocks = -1;
        lastPlaybackTickAt = -1L;
    }

    public synchronized void tickPlayback() {
        if (!running) {
            lastPlaybackTickAt = -1L;
            return;
        }
        long previousPlaybackTickAt = lastPlaybackTickAt;
        lastPlaybackTickAt = System.currentTimeMillis();

        if (noteBlocks != null && tuned) {
            while (running) {
                long note = song.notes[index];
                if ((short)note <= Math.round(tick)) {
                    @Nullable BlockPos blockPos = noteBlocks.get(Note.INSTRUMENTS[(byte)(note >> Note.INSTRUMENT_SHIFT)]).get((byte)(note >> Note.NOTE_SHIFT));
                    if (blockPos == null) {
                        // Instrument got likely mapped to "nothing". Skip it
                        index++;
                        continue;
                    }
                    if (!canInteractWithBlock(blockPos, 5.5)) {
                        stop();
                        LOG.error("Too far to reach {}", blockPos);
                        return;
                    }

                    Proxy.getInstance().getClient().getChannel().write(
                            new ServerboundPlayerActionPacket(PlayerAction.START_DESTROY_BLOCK,
                                blockPos.x(), blockPos.y(), blockPos.z(),
                                Direction.UP, CACHE.getPlayerCache().getSeqId().incrementAndGet()));

                    if (PLUGIN_CONFIG.discJockey.rotateToBlock) {
                        var rotation = RotationHelper.shortestRotationTo(blockPos.x(), blockPos.y(), blockPos.z());
                        INPUTS.submit(InputRequest.builder() // this is purely cosmetic
                                .yaw(rotation.getX())
                                .pitch(rotation.getY())
                                .priority(5)
                                .build());
                    }

                    index++;
                    if (index >= song.notes.length) {
                        stop();
                        if (PLUGIN_CONFIG.discJockey.loopSong) {
                            start(song);
                        }
                        break;
                    }
                } else {
                    break;
                }
            }

            if (running) { // Might not be running anymore (prevent small offset on song, even if that is not played anymore)
                long elapsedMs = previousPlaybackTickAt != -1L && lastPlaybackTickAt != -1L ? lastPlaybackTickAt - previousPlaybackTickAt : (16); // Assume 16ms if unknown
                tick += song.millisecondsToTicks(elapsedMs) * PLUGIN_CONFIG.discJockey.playbackSpeed;
                Proxy.getInstance().getClient().getChannel().flush();
            }
        }
    }


    private void handleBotTick(ClientBotTick event) {
        BlockPos playerPos = CACHE.getPlayerCache().getThePlayer().blockPos();


        boolean inQueue = Proxy.getInstance().isOn2b2t() && (Proxy.getInstance().isInQueue() || !World.isChunkLoadedBlockPos(playerPos.x(), playerPos.z()));
        if (inQueue != this.inQueue) {
            if (!inQueue) {
                ticksUntillJump = 60;
            }
            this.inQueue = inQueue;
        }

        if (BARITONE.isActive() || inQueue) {
            tempPaused = true;
            pause();
            return;
        } else if (song != null && !running && tempPaused) {
            resume();
        }

        if (ticksUntillJump > 0) { // jump to prevent 2b2ts antispam from preventing outgoing commands
            ticksUntillJump--;
            if (ticksUntillJump == 10) {
                INPUTS.submit(InputRequest.builder()
                        .input(Input.builder().jumping(true).build())
                        .priority(100000)
                        .build());
            }
            return;
        }

        if (song == null && PLUGIN_CONFIG.discJockey.shuffle) {
            playRandomSong();
        }

        if (CACHE.getPlayerCache().getInventoryCache().getPlayerInventory().getItemStack(36 + CACHE.getPlayerCache().getHeldItemSlot()) != null) {
            int hotbarSlot = 0;
            for (int i = 36; i < 44; i++) {
                if (CACHE.getPlayerCache().getInventoryCache().getPlayerInventory().getItemStack(i) == null) {
                    sendClientPacket(new ServerboundSetCarriedItemPacket(hotbarSlot));
                    return;
                }
                hotbarSlot++;
            }
            LOG.error("Unable to find empty hotbar slot, cannot play song");
            stop();
            return;
        }



        if (song == null || !running) return;


        // Clear outdated note predictions
        ArrayList<BlockPos> outdatedPredictions = new ArrayList<>();
        for (Map.Entry<BlockPos, Pair<Integer, Long>> entry : notePredictions.entrySet()) {
            if (entry.getValue().right() < System.currentTimeMillis())
                outdatedPredictions.add(entry.getKey());
        }
        for (BlockPos outdatedPrediction : outdatedPredictions) notePredictions.remove(outdatedPrediction);

        if (noteBlocks == null) {
            // Create list of available noteblock positions per used instrument
            HashMap<NoteBlockInstrument, ArrayList<BlockPos>> noteblocksForInstrument = new HashMap<>();
            for (NoteBlockInstrument instrument : NoteBlockInstrument.values()) noteblocksForInstrument.put(instrument, new ArrayList<>());

            final int maxOffset = 7;

            for (NoteBlockInstrument instrument : noteblocksForInstrument.keySet().toArray(new NoteBlockInstrument[0])) {
                for (int x = -maxOffset; x < maxOffset; x++) {
                    for (int y = -maxOffset; y < maxOffset; y++) {
                        for (int z = -maxOffset; z < maxOffset; z++) {
                            BlockPos offset = playerPos.add(x, y, z);
                            if (!World.isChunkLoadedBlockPos(offset.x(), offset.z())) return;

                            if (!canInteractWithBlock(offset, 5.5)) continue;

                            Block block = World.getBlock(offset);

                            if (block != BlockRegistry.NOTE_BLOCK || !World.getBlock(offset.above()).name().contains("air")) continue;

                            if (World.getBlockStateProperty(World.getBlockStateId(offset), BlockStateProperties.NOTEBLOCK_INSTRUMENT) == instrument) {
                                noteblocksForInstrument.get(instrument).add(offset);
                            }
                        }
                    }
                }
            }


            noteBlocks = new HashMap<>();
            // Remap instruments for funzies
            if (!instrumentMap.isEmpty()) {
                HashMap<NoteBlockInstrument, ArrayList<BlockPos>> newNoteblocksForInstrument = new HashMap<>();
                for (NoteBlockInstrument orig : noteblocksForInstrument.keySet()) {
                    NoteBlockInstrument mappedInstrument = instrumentMap.getOrDefault(orig, orig);
                    if (mappedInstrument == null) {
                        // Instrument got likely mapped to "nothing"
                        newNoteblocksForInstrument.put(orig, null);
                        continue;
                    }

                    newNoteblocksForInstrument.put(orig, noteblocksForInstrument.getOrDefault(instrumentMap.getOrDefault(orig, orig), new ArrayList<>()));
                }
                noteblocksForInstrument = newNoteblocksForInstrument;
            }

            // Find fitting noteblocks with the least amount of adjustments required (to reduce tuning time)
            ArrayList<Note> capturedNotes = new ArrayList<>();
            for (Note note : song.uniqueNotes) {
                ArrayList<BlockPos> availableBlocks = noteblocksForInstrument.get(note.instrument());
                if (availableBlocks == null) {
                    // Note was mapped to "nothing". Pretend it got captured, but just ignore it
                    capturedNotes.add(note);
                    getNotes(note.instrument()).put(note.note(), null);
                    continue;
                }
                BlockPos bestBlockPos = null;
                int bestBlockTuningSteps = Integer.MAX_VALUE;
                for (BlockPos blockPos : availableBlocks) {
                    int wantedNote = note.note();
                    Integer currentNote = World.getBlockStateProperty(World.getBlockStateId(blockPos), BlockStateProperties.NOTE);
                    if (currentNote == null) {
                        // Noteblock is not a noteblock anymore, or was never a noteblock
                        continue;
                    }
                    int tuningSteps = wantedNote >= currentNote ? wantedNote - currentNote : (25 - currentNote) + wantedNote;

                    if ( tuningSteps < bestBlockTuningSteps) {
                        bestBlockPos = blockPos;
                        bestBlockTuningSteps = tuningSteps;
                    }
                }

                if (bestBlockPos != null) {
                    capturedNotes.add(note);
                    availableBlocks.remove(bestBlockPos);
                    getNotes(note.instrument()).put(note.note(), bestBlockPos);
                } // else will be a missing note
            }

            ArrayList<Note> missingNotes = new ArrayList<>(song.uniqueNotes);
            missingNotes.removeAll(capturedNotes);
            if (!missingNotes.isEmpty()) {
                LOG.error("Missing " + missingNotes.size() + " notes, cannot play song");
                HashMap<Block, Integer> missing = new HashMap<>();
                for (Note note : missingNotes) {
                    NoteBlockInstrument mappedInstrument = instrumentMap.getOrDefault(note.instrument(), note.instrument());
                    if (mappedInstrument == null) continue; // Ignore if mapped to nothing
                    Block block = Note.INSTRUMENT_BLOCKS.get(mappedInstrument);
                    Integer got = missing.get(block);
                    if (got == null) got = 0;
                    missing.put(block, got + 1);
                }

                missingInstrumentBlocks = missing;
                missing.forEach((block, integer) -> LOG.error("player.invalid_note_blocks missing " + integer + " of " + BlockRegistry.REGISTRY.get(block.id()).name()));
                stop();
            }
        } else if (!tuned) {

            if (lastInteractAt != -1L) {
                availableInteracts += ((System.currentTimeMillis() - lastInteractAt) / (310.0f / 8.0f));
                availableInteracts = Math.min(8f, Math.max(0f, availableInteracts));
            } else {
                availableInteracts = 8;
                lastInteractAt = System.currentTimeMillis();
            }

            int fullyTunedBlocks = 0;
            HashMap<BlockPos, Integer> untunedNotes = new HashMap<>();
            for (Note note : song.uniqueNotes) {
                if (noteBlocks == null || noteBlocks.get(note.instrument()) == null)
                    continue;
                BlockPos blockPos = noteBlocks.get(note.instrument()).get(note.note());
                if (blockPos == null) continue;
                int blockState = World.getBlockStateId(blockPos);

                Integer blockStateNote = World.getBlockStateProperty(blockState, BlockStateProperties.NOTE);

                Integer assumedNote = notePredictions.containsKey(blockPos) ? notePredictions.get(blockPos).left() : blockStateNote;
                if (assumedNote == null) {
                    LOG.warn("noteblock at " + blockPos + " is not a noteblock anymore, or was never a noteblock");
                    continue;
                }

                if (blockStateNote != null) {
                    if (assumedNote == note.note() && blockStateNote == note.note())
                        fullyTunedBlocks++;

                    if (assumedNote != note.note()) {
                        if (!canInteractWithBlock(blockPos, 5.5)) {
                            stop();
                            LOG.error("I ain't got arms that long dawg, too far to interact with noteblock at " + blockPos);
                            return;
                        }
                        untunedNotes.put(blockPos, blockStateNote);
                    }
                } else {
                    noteBlocks = null;
                    break;
                }
            }

            if (tuneInitialUntunedBlocks == -1 || tuneInitialUntunedBlocks < untunedNotes.size())
                tuneInitialUntunedBlocks = untunedNotes.size();

            int existingUniqueNotesCount = 0;
            for (Note n : song.uniqueNotes) {
                if (noteBlocks.get(n.instrument()).get(n.note()) != null)
                    existingUniqueNotesCount++;
            }

            if (untunedNotes.isEmpty() && fullyTunedBlocks == existingUniqueNotesCount) {
                LOG.info("it looks like we are tuned now!");
                tuned = true;
                // Wait roundrip + 100ms before considering tuned after changing notes (in case the server rejects an interact)
//                if (lastInteractAt == -1 || System.currentTimeMillis() - lastInteractAt >= 1500) {
//                    tuned = true;
//                    tuneInitialUntunedBlocks = -1;
//                }
            }

            int lastTunedNote = Integer.MIN_VALUE;
            while (availableInteracts >= 1f && !untunedNotes.isEmpty()) {
                BlockPos blockPos = null;
                int searches = 0;
                while(blockPos == null) {
                    searches++;
                    // Find higher note
                    for (Map.Entry<BlockPos, Integer> entry : untunedNotes.entrySet()) {
                        if (entry.getValue() > lastTunedNote) {
                            blockPos = entry.getKey();
                            break;
                        }
                    }
                    // Find higher note or equal
                    if (blockPos == null) {
                        for (Map.Entry<BlockPos, Integer> entry : untunedNotes.entrySet()) {
                            if (entry.getValue() >= lastTunedNote) {
                                blockPos = entry.getKey();
                                break;
                            }
                        }
                    }
                    // Not found. Reset last note
                    if (blockPos == null)
                        lastTunedNote = Integer.MIN_VALUE;
                    if (blockPos == null && searches > 1) {
                        // Something went wrong. Take any note (one should at least exist here)
                        blockPos = untunedNotes.keySet().toArray(new BlockPos[0])[0];
                        break;
                    }
                }
                if (blockPos == null) return; // Something went very, very wrong!

                lastTunedNote = untunedNotes.get(blockPos);
                untunedNotes.remove(blockPos);
                Integer assumedNote = notePredictions.containsKey(blockPos) ? notePredictions.get(blockPos).left() : World.getBlockStateProperty(World.getBlockStateId(blockPos), BlockStateProperties.NOTE);

                if (assumedNote == null) {
                    LOG.warn("Noteblock at " + blockPos + " is not a noteblock anymore, or was never a noteblock");
                    continue;
                }

                notePredictions.put(blockPos, new Pair<>((assumedNote + 1) % 25, 1000L));

                 Proxy.getInstance().getClient().getChannel().writeAndFlush(new ServerboundUseItemOnPacket(
                        blockPos.x(), blockPos.y(), blockPos.z(),
                        Direction.UP, Hand.MAIN_HAND, 0.5f, 0.5f, 0.5f,
                        false, false, CACHE.getPlayerCache().getSeqId().incrementAndGet()
                ));

                lastInteractAt = System.currentTimeMillis();
                availableInteracts -= 1f;
            }
        }
    }

    private HashMap<Byte, BlockPos> getNotes(NoteBlockInstrument instrument) {
        return noteBlocks.computeIfAbsent(instrument, k -> new HashMap<>());
    }

    public static boolean canInteractWithBlock(BlockPos pos, double interactionRange) {
        var pc = CACHE.getPlayerCache();
        double px = pc.getX();
        double py = pc.getEyeY();
        double pz = pc.getZ();

        double minX = pos.x();
        double minY = pos.y();
        double minZ = pos.z();
        double maxX = minX + 1.0;
        double maxY = minY + 1.0;
        double maxZ = minZ + 1.0;

        double dx = px < minX ? (minX - px) : (px > maxX ? (px - maxX) : 0.0);
        double dy = py < minY ? (minY - py) : (py > maxY ? (py - maxY) : 0.0);
        double dz = pz < minZ ? (minZ - pz) : (pz > maxZ ? (pz - maxZ) : 0.0);

        double dist2 = dx * dx + dy * dy + dz * dz;
        return dist2 < interactionRange * interactionRange;
    }
}
