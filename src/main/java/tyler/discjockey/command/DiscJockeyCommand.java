package tyler.discjockey.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import tyler.discjockey.module.DiscJockeyModule;
import tyler.discjockey.utils.Song;
import tyler.discjockey.utils.SongFinder;
import tyler.discjockey.utils.SongLoader;
import java.util.Optional;

import static com.mojang.brigadier.arguments.FloatArgumentType.floatArg;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static tyler.discjockey.DiscJockeyPlugin.PLUGIN_CONFIG;

public class DiscJockeyCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("discJockey")
                .category(CommandCategory.MODULE)
                .description("""
                Disc Jockey command
                """)
                .usageLines(
                        "on/off",
                        "play <songName>",
                        "playbackSpeed <value>",
                        "loopSong <true/false>",
                        "shuffle <true/false>",
                        "rotateToBlock <true/false>",
                        "stop",
                        "pause",
                        "resume",
                        "skip"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("discJockey")
                // Enabled
                .then(argument("value", toggle()).executes(c -> {
                    PLUGIN_CONFIG.discJockey.enabled = getToggle(c, "value");
                    MODULE.get(DiscJockeyModule.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("Enabled set to " + PLUGIN_CONFIG.discJockey.enabled);
                }))

                // Playback Speed
                .then(literal("playbackSpeed").then(argument("value", floatArg()).executes(c -> {
                    PLUGIN_CONFIG.discJockey.playbackSpeed = c.getArgument("value", Float.class);
                    c.getSource().getEmbed()
                            .title("Playback speed set to " + PLUGIN_CONFIG.discJockey.playbackSpeed);
                })))
                // Loop Song
                .then(literal("loopSong").then(argument("value", toggle()).executes(c -> {
                    PLUGIN_CONFIG.discJockey.loopSong = getToggle(c, "value");
                    c.getSource().getEmbed()
                            .title("Loop song set to " + PLUGIN_CONFIG.discJockey.loopSong);
                })))
                // Shuffle
                .then(literal("shuffle").then(argument("value", toggle()).executes(c -> {
                    PLUGIN_CONFIG.discJockey.shuffle = getToggle(c, "value");
                    c.getSource().getEmbed()
                            .title("Shuffle set to " + PLUGIN_CONFIG.discJockey.shuffle);
                })))
                // Rotate To Block
                .then(literal("rotateToBlock").then(argument("value", toggle()).executes(c -> {
                    PLUGIN_CONFIG.discJockey.rotateToBlock = getToggle(c, "value");
                    c.getSource().getEmbed()
                            .title("Rotate to block set to " + PLUGIN_CONFIG.discJockey.rotateToBlock);
                })))
                // Play song
                .then(literal("play").then(argument("song", wordWithChars()).executes(c -> {
                    String userInputRaw = c.getArgument("song", String.class);
                    if (!SongLoader.loadingSongs) {
                        Optional<Song> best = SongFinder.findBestMatchingSong(userInputRaw);
                        if (best.isPresent()) {
                            MODULE.get(DiscJockeyModule.class).start(best.get());
                            c.getSource().getEmbed()
                                    .title("Playing")
                                    .description("Matched: " + best.get().displayName);
                        } else {
                            c.getSource().getEmbed()
                                    .title("Song not found")
                                    .description("No close match found for: " + userInputRaw);
                        }
                    }
                })))
                //Pause
                .then(literal("pause").executes(c -> {
                    MODULE.get(DiscJockeyModule.class).pause();
                    c.getSource().getEmbed()
                            .title("Disc Jockey Paused")
                            .description("The disc jockey has been paused.");
                }))
                // Resume
                .then(literal("resume").executes(c -> {
                    MODULE.get(DiscJockeyModule.class).resume();
                    c.getSource().getEmbed()
                            .title("Disc Jockey Resumed")
                            .description("The disc jockey has been resumed.");
                }))
                // Skip
                .then(literal("skip").executes(c -> {
                    MODULE.get(DiscJockeyModule.class).playRandomSong();
                    c.getSource().getEmbed()
                            .title("Disc Jockey Skipped")
                            .description("The disc jockey has skipped to the next song.");
                }))
                // Stop
                .then(literal("stop").executes(c -> {
                    MODULE.get(DiscJockeyModule.class).stop();
                    c.getSource().getEmbed()
                            .title("Disc Jockey Stopped")
                            .description("The disc jockey has been stopped.");
                }));
    }


    @Override
    public void defaultEmbed(Embed embed) {
        embed
                .primaryColor()
                .addField("Enabled", toggleStr(PLUGIN_CONFIG.discJockey.enabled))
                .addField("Songs Loaded", String.valueOf(SongLoader.SONGS.size()))
                .addField("Playback Speed", String.valueOf(PLUGIN_CONFIG.discJockey.playbackSpeed))
                .addField("Loop Song", toggleStr(PLUGIN_CONFIG.discJockey.loopSong))
                .addField("Shuffle", toggleStr(PLUGIN_CONFIG.discJockey.shuffle))
                .addField("Rotate To Block", toggleStr(PLUGIN_CONFIG.discJockey.rotateToBlock));
    }
}
