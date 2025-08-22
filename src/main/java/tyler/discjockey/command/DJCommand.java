package tyler.discjockey.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.command.brigadier.ZRequiredArgumentBuilder;
import tyler.discjockey.module.DiscJockeyModule;
import tyler.discjockey.utils.Song;
import tyler.discjockey.utils.SongFinder;
import tyler.discjockey.utils.SongLoader;

import java.util.List;
import java.util.Optional;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.zenith.Globals.MODULE;
import static tyler.discjockey.DiscJockeyPlugin.PLUGIN_CONFIG;

public class DJCommand extends com.zenith.command.api.Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("dj")
                .category(CommandCategory.MODULE)
                .description("""
                DJ command
                """)
                .usageLines(
                        "help",
                        "info",
                        "play <song>            (queue at end)",
                        "playNow <song>         (start immediately)",
                        "playNext <song>        (queue to play next)",
                        "skip                   (advance to next/queue)",
                        "pause",
                        "resume",
                        "stop",
                        "random | playRandom",
                        "queue show | list",
                        "queue add <song>",
                        "queue next <song>",
                        "queue clear",
                        "queue shuffle",
                        "queue repeat <on|off>"
                )
                .build();
    }

    private static String songLine(Song s) {
        String author = s.author == null || s.author.isEmpty() ? "" : " by: " + s.author;
        int lenSec = (int) s.getLengthInSeconds();
        return s.displayName + author + " (Length: " + lenSec + "s)";
    }

    public String getCurrentSongInfo() {
        DiscJockeyModule djModule = MODULE.get(DiscJockeyModule.class);
        if (djModule.song != null) {
            return "Now Playing: " + songLine(djModule.song);
        } else {
            return "No song is currently playing.";
        }
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        // --- HELP ---
        Command<CommandContext> execHelp = c -> {
            c.getSource().getEmbed()
                    .title("Commands: !dj play <song> !dj playnow <song> !dj pause !dj resume !dj random !dj skip")
                    .description(String.join("\n",
                            "info",
                            "play <song>",
                            "playNow <song>",
                            "playNext <song>",
                            "skip",
                            "pause",
                            "resume",
                            "stop",
                            "random | playRandom",
                            "queue show | list",
                            "queue add <song>",
                            "queue next <song>",
                            "queue clear",
                            "queue shuffle",
                            "queue repeat <on|off>"
                    ));
            return 1;
        };

        // --- INFO ---
        Command<CommandContext> execInfo = c -> {
            c.getSource().getEmbed().title(getCurrentSongInfo());
            return 1;
        };

        // --- PLAY (QUEUE) ---
        Command<CommandContext> execPlayQueue = c -> {
            String userInputRaw = c.getArgument("song", String.class);
            if (SongLoader.loadingSongs) return 1;

            DiscJockeyModule dj = MODULE.get(DiscJockeyModule.class);
            Optional<Song> best = SongFinder.findBestMatchingSong(userInputRaw);
            if (best.isPresent()) {
                // NOTE: Module.enqueue() will start immediately if nothing is playing.
                // If you want strictly "queue-only never starts," we can add an enqueueOnly method in the module.
                dj.enqueue(best.get());
                List<String> q = dj.getQueueDisplay();
                int pos = q.size(); // new item is at end
                c.getSource().getEmbed()
                        .title("Queued: " + songLine(best.get()))
                        .description("Position in queue: " + pos);
            } else {
                c.getSource().getEmbed()
                        .title("Song not found")
                        .description("No close match found for: " + userInputRaw);
            }
            return 1;
        };

        // --- PLAY NOW (IMMEDIATE START) ---
        Command<CommandContext> execPlayNow = c -> {
            String userInputRaw = c.getArgument("song", String.class);
            if (SongLoader.loadingSongs) return 1;

            DiscJockeyModule dj = MODULE.get(DiscJockeyModule.class);
            Optional<Song> best = SongFinder.findBestMatchingSong(userInputRaw);
            if (best.isPresent()) {
                dj.start(best.get());
                c.getSource().getEmbed()
                        .title("Now Playing: " + songLine(best.get()))
                        .description("Matched: " + best.get().displayName);
            } else {
                c.getSource().getEmbed()
                        .title("Song not found")
                        .description("No close match found for: " + userInputRaw);
            }
            return 1;
        };

        // --- PLAY NEXT (QUEUE FRONT) ---
        Command<CommandContext> execPlayNext = c -> {
            String userInputRaw = c.getArgument("song", String.class);
            if (SongLoader.loadingSongs) return 1;

            DiscJockeyModule dj = MODULE.get(DiscJockeyModule.class);
            Optional<Song> best = SongFinder.findBestMatchingSong(userInputRaw);
            if (best.isPresent()) {
                dj.enqueueNext(best.get());
                c.getSource().getEmbed()
                        .title("Queued Next: " + songLine(best.get()))
                        .description("Will play after the current song.");
            } else {
                c.getSource().getEmbed()
                        .title("Song not found")
                        .description("No close match found for: " + userInputRaw);
            }
            return 1;
        };

        // --- RANDOM ---
        Command<CommandContext> execRandom = c -> {
            DiscJockeyModule dj = MODULE.get(DiscJockeyModule.class);
            dj.playRandomSong();
            c.getSource().getEmbed()
                    .title(getCurrentSongInfo())
                    .description("Picked a random song.");
            return 1;
        };

        // --- PAUSE/RESUME/STOP ---
        Command<CommandContext> execPause = c -> {
            MODULE.get(DiscJockeyModule.class).pause();
            c.getSource().getEmbed()
                    .title("Paused")
                    .description(getCurrentSongInfo());
            return 1;
        };

        Command<CommandContext> execResume = c -> {
            MODULE.get(DiscJockeyModule.class).resume();
            c.getSource().getEmbed()
                    .title("Resumed")
                    .description(getCurrentSongInfo());
            return 1;
        };

        Command<CommandContext> execStop = c -> {
            MODULE.get(DiscJockeyModule.class).stop();
            c.getSource().getEmbed()
                    .title("Stopped")
                    .description("Playback stopped. Queue preserved.");
            return 1;
        };

        // --- SKIP (ADVANCE QUEUE) ---
        Command<CommandContext> execSkip = c -> {
            DiscJockeyModule dj = MODULE.get(DiscJockeyModule.class);
            boolean advanced = dj.skip();
            if (!advanced) {
                if (PLUGIN_CONFIG.discJockey.shuffle) {
                    dj.playRandomSong();
                    c.getSource().getEmbed()
                            .title(getCurrentSongInfo())
                            .description("Skipped to a random song.");
                } else {
                    c.getSource().getEmbed()
                            .title("Skipped")
                            .description("No songs in queue. " + getCurrentSongInfo());
                }
            } else {
                c.getSource().getEmbed()
                        .title(getCurrentSongInfo())
                        .description("Skipped current song.");
            }
            return 1;
        };

        // --- QUEUE SHOW/CLEAR/SHUFFLE/REPEAT ---
        Command<CommandContext> execQueueShow = c -> {
            DiscJockeyModule dj = MODULE.get(DiscJockeyModule.class);
            List<String> q = dj.getQueueDisplay();
            if (q.isEmpty()) {
                c.getSource().getEmbed()
                        .title("Queue (0)")
                        .description("Queue is empty.");
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < q.size(); i++) {
                    sb.append(i + 1).append(". ").append(q.get(i)).append("\n");
                }
                c.getSource().getEmbed()
                        .title("Queue (" + q.size() + ")")
                        .description(sb.toString());
            }
            return 1;
        };

        Command<CommandContext> execQueueAdd = execPlayQueue;

        Command<CommandContext> execQueueNext = execPlayNext;

        Command<CommandContext> execQueueClear = c -> {
            MODULE.get(DiscJockeyModule.class).clearQueue();
            c.getSource().getEmbed()
                    .title("Queue Cleared")
                    .description("Removed all upcoming songs.");
            return 1;
        };

        Command<CommandContext> execQueueShuffle = c -> {
            MODULE.get(DiscJockeyModule.class).shuffleQueue();
            c.getSource().getEmbed()
                    .title("Queue Shuffled")
                    .description("Randomized upcoming songs.");
            return 1;
        };

        Command<CommandContext> execQueueRepeatOn = c -> {
            MODULE.get(DiscJockeyModule.class).setRepeatQueue(true);
            c.getSource().getEmbed().title("Repeat Queue: ON");
            return 1;
        };

        Command<CommandContext> execQueueRepeatOff = c -> {
            MODULE.get(DiscJockeyModule.class).setRepeatQueue(false);
            c.getSource().getEmbed().title("Repeat Queue: OFF");
            return 1;
        };

        // Build the tree
        return command("dj")
                .then(literal("help")
                        .executes(execHelp)
                        .then(greedyTail().executes(execHelp)))
                .then(literal("info")
                        .executes(execInfo)
                        .then(greedyTail().executes(execInfo)))

                // play variants
                .then(literal("play")
                        .then(argument("song", greedyString()).executes(execPlayQueue)))
                .then(literal("playNow")
                        .then(argument("song", greedyString()).executes(execPlayNow)))
                .then(literal("playNext")
                        .then(argument("song", greedyString()).executes(execPlayNext)))

                // search/song aliases for play (queue)
                .then(literal("search")
                        .then(argument("song", greedyString()).executes(execPlayQueue)))
                .then(literal("song")
                        .then(argument("song", greedyString()).executes(execPlayQueue)))

                // queue group
                .then(literal("queue")
                        .then(literal("show").executes(execQueueShow).then(greedyTail().executes(execQueueShow)))
                        .then(literal("list").executes(execQueueShow).then(greedyTail().executes(execQueueShow)))
                        .then(literal("add").then(argument("song", greedyString()).executes(execQueueAdd)))
                        .then(literal("next").then(argument("song", greedyString()).executes(execQueueNext)))
                        .then(literal("clear").executes(execQueueClear).then(greedyTail().executes(execQueueClear)))
                        .then(literal("shuffle").executes(execQueueShuffle).then(greedyTail().executes(execQueueShuffle)))
                        .then(literal("repeat")
                                .then(literal("on").executes(execQueueRepeatOn))
                                .then(literal("off").executes(execQueueRepeatOff))
                        )
                )

                // transport & misc
                .then(literal("skip").executes(execSkip).then(greedyTail().executes(execSkip)))
                .then(literal("pause").executes(execPause).then(greedyTail().executes(execPause)))
                .then(literal("resume").executes(execResume).then(greedyTail().executes(execResume)))
                .then(literal("stop").executes(execStop).then(greedyTail().executes(execStop)))
                .then(literal("random").executes(execRandom).then(greedyTail().executes(execRandom)))
                .then(literal("playRandom").executes(execRandom).then(greedyTail().executes(execRandom)))

                // default
                .then(greedyTail().executes(execInfo))
                .executes(execInfo);
    }

    private ZRequiredArgumentBuilder<CommandContext, String> greedyTail() {
        return argument("greedy", greedyString());
    }
}
