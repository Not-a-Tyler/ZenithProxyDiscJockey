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
import java.util.Optional;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.zenith.Globals.MODULE;

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
                        "stop",
                        "pause",
                        "resume",
                        "skip",
                        "random",
                        "playRandom",
                        "play <song>",
                        "info"
                )
                .build();
    }

    public String getCurrentSongInfo() {
        DiscJockeyModule djModule = MODULE.get(DiscJockeyModule.class);
        if (djModule.song != null) {
            return "Currently Playing " + djModule.song.displayName + (djModule.song.author.isEmpty() ? "" : " by: " + djModule.song.author) +
                   " (Length: " + ((int) djModule.song.getLengthInSeconds()) + " seconds)";
        } else {
            return "No song is currently playing.";
        }
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        Command<CommandContext> execPlay = c -> {
            String userInputRaw = c.getArgument("song", String.class);
            if (!SongLoader.loadingSongs) {
                Optional<Song> best = SongFinder.findBestMatchingSong(userInputRaw);
                if (best.isPresent()) {
                    MODULE.get(DiscJockeyModule.class).start(best.get());
                    c.getSource().getEmbed()
                            .title(getCurrentSongInfo())
                            .description("Matched: " + best.get().displayName);
                } else {
                    c.getSource().getEmbed()
                            .title("Song not found")
                            .description("No close match found for: " + userInputRaw);
                }
            }
            return 1;
        };

        Command<CommandContext> execPause = c -> {
            MODULE.get(DiscJockeyModule.class).pause();
            c.getSource().getEmbed().title("Disc Jockey Paused").description("The disc jockey has been paused.");
            return 1;
        };

        Command<CommandContext> execResume = c -> {
            MODULE.get(DiscJockeyModule.class).resume();
            c.getSource().getEmbed().title("Disc Jockey Resumed " + getCurrentSongInfo()).description("The disc jockey has been resumed.");
            return 1;
        };

        Command<CommandContext> execRandom = c -> {
            MODULE.get(DiscJockeyModule.class).playRandomSong();
            c.getSource().getEmbed().title("Disc Jockey Playing New Random Song " + getCurrentSongInfo()).description("The disc jockey has skipped to the next song.");
            return 1;
        };

        Command<CommandContext> execStop = c -> {
            MODULE.get(DiscJockeyModule.class).stop();
            c.getSource().getEmbed().title("Disc Jockey Stopped").description("The disc jockey has been stopped.");
            return 1;
        };

        Command<CommandContext> execInfo = c -> {
            c.getSource().getEmbed().title(getCurrentSongInfo());
            return 1;
        };

        // Build the tree (all literals reside here)
        return command("dj")
                .then(literal("play")
                        .then(argument("song", greedyString()).executes(execPlay)))
                .then(literal("search")
                        .then(argument("song", greedyString()).executes(execPlay)))
                .then(literal("song")
                        .then(argument("song", greedyString()).executes(execPlay)))
                .then(literal("pause")
                        .executes(execPause)
                        .then(greedyTail().executes(execPause)))
                .then(literal("resume")
                        .executes(execResume)
                        .then(greedyTail().executes(execResume)))
                .then(literal("skip")
                        .executes(execRandom)
                        .then(greedyTail().executes(execRandom)))
                .then(literal("random")
                        .executes(execRandom)
                        .then(greedyTail().executes(execRandom)))
                .then(literal("playRandom")
                        .executes(execRandom)
                        .then(greedyTail().executes(execRandom)))
                .then(literal("info")
                        .executes(execInfo)
                        .then(greedyTail().executes(execInfo)))
                .then(literal("stop")
                        .executes(execStop)
                        .then(greedyTail().executes(execStop)))
                .then(greedyTail().executes(execInfo))
                .executes(execInfo);
    }

    private ZRequiredArgumentBuilder<CommandContext, String> greedyTail() {
        return argument("greedy", greedyString());
    }
}
