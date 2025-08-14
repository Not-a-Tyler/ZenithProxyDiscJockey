package tyler.discjockey.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import tyler.discjockey.module.DiscJockeyModule;
import tyler.discjockey.utils.SongLoader;

import static com.mojang.brigadier.arguments.FloatArgumentType.floatArg;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static tyler.discjockey.DiscJockeyPlugin.PLUGIN_CONFIG;

public class DiscJockeyCommand extends com.zenith.command.api.Command {
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
                        "playbackSpeed <value>",
                        "loopSong <true/false>",
                        "shuffle <true/false>",
                        "rotateToBlock <true/false>",
                        "chatControl <true/false>",
                        "commandPrefix <prefix>"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        // Exec handlers (lambdas defined inside register)
        Command<CommandContext> execEnable = c -> {
            PLUGIN_CONFIG.discJockey.enabled = getToggle(c, "value");
            MODULE.get(DiscJockeyModule.class).syncEnabledFromConfig();
            c.getSource().getEmbed().title("Enabled set to " + PLUGIN_CONFIG.discJockey.enabled);
            return 1;
        };

        Command<CommandContext> execPlaybackSpeed = c -> {
            PLUGIN_CONFIG.discJockey.playbackSpeed = c.getArgument("value", Float.class);
            c.getSource().getEmbed().title("Playback speed set to " + PLUGIN_CONFIG.discJockey.playbackSpeed);
            return 1;
        };

        Command<CommandContext> execLoopSong = c -> {
            PLUGIN_CONFIG.discJockey.loopSong = getToggle(c, "value");
            c.getSource().getEmbed().title("Loop song set to " + PLUGIN_CONFIG.discJockey.loopSong);
            return 1;
        };

        Command<CommandContext> execShuffle = c -> {
            PLUGIN_CONFIG.discJockey.shuffle = getToggle(c, "value");
            System.out.println("set shufdiscJockey shuffle off");
            c.getSource().getEmbed().title("Shuffle set to " + PLUGIN_CONFIG.discJockey.shuffle);
            return 1;
        };

        Command<CommandContext> execRotateToBlock = c -> {
            PLUGIN_CONFIG.discJockey.rotateToBlock = getToggle(c, "value");
            c.getSource().getEmbed().title("Rotate to block set to " + PLUGIN_CONFIG.discJockey.rotateToBlock);
            return 1;
        };

        Command<CommandContext> execChatControl = c -> {
            PLUGIN_CONFIG.discJockey.chatControl = getToggle(c, "value");
            c.getSource().getEmbed().title("Chat control set to " + PLUGIN_CONFIG.discJockey.chatControl);
            return 1;
        };

        Command<CommandContext> execCommandPrefix = c -> {
            String s = c.getArgument("prefix", String.class);
            if (s != null && s.length() >= 2) {
                char f = s.charAt(0), l = s.charAt(s.length() - 1);
                if ((f == '"' && l == '"') || (f == '\'' && l == '\'')) {
                    s = s.substring(1, s.length() - 1);
                }
            }
            PLUGIN_CONFIG.discJockey.commandPrefix = s;
            c.getSource().getEmbed().title("Command prefix set to " + PLUGIN_CONFIG.discJockey.commandPrefix);
            return 1;
        };

        // Build the literal root directly with Brigadier
        return command("discjockey")
                .then(RequiredArgumentBuilder.<CommandContext, Boolean>argument("value", toggle()).executes(execEnable))

                .then(LiteralArgumentBuilder.<CommandContext>literal("playbackSpeed")
                        .then(RequiredArgumentBuilder.<CommandContext, Float>argument("value", floatArg()).executes(execPlaybackSpeed)))

                .then(LiteralArgumentBuilder.<CommandContext>literal("loopSong")
                        .then(RequiredArgumentBuilder.<CommandContext, Boolean>argument("value", toggle()).executes(execLoopSong)))

                .then(LiteralArgumentBuilder.<CommandContext>literal("shuffle")
                        .then(RequiredArgumentBuilder.<CommandContext, Boolean>argument("value", toggle()).executes(execShuffle)))

                .then(LiteralArgumentBuilder.<CommandContext>literal("rotateToBlock")
                        .then(RequiredArgumentBuilder.<CommandContext, Boolean>argument("value", toggle()).executes(execRotateToBlock)))

                .then(LiteralArgumentBuilder.<CommandContext>literal("chatControl")
                        .then(RequiredArgumentBuilder.<CommandContext, Boolean>argument("value", toggle()).executes(execChatControl)))

                .then(LiteralArgumentBuilder.<CommandContext>literal("commandPrefix")
                        .then(RequiredArgumentBuilder.<CommandContext, String>argument("prefix", string()).executes(execCommandPrefix)));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed.primaryColor()
                .addField("Enabled", toggleStr(PLUGIN_CONFIG.discJockey.enabled))
                .addField("Songs Loaded", String.valueOf(SongLoader.SONGS.size()))
                .addField("Playback Speed", String.valueOf(PLUGIN_CONFIG.discJockey.playbackSpeed))
                .addField("Loop Song", toggleStr(PLUGIN_CONFIG.discJockey.loopSong))
                .addField("Shuffle", toggleStr(PLUGIN_CONFIG.discJockey.shuffle))
                .addField("Rotate To Block", toggleStr(PLUGIN_CONFIG.discJockey.rotateToBlock))
                .addField("Chat Control", toggleStr(PLUGIN_CONFIG.discJockey.chatControl))
                .addField("Command Prefix", String.valueOf(PLUGIN_CONFIG.discJockey.commandPrefix));
    }
}
