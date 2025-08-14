package tyler.discjockey;

import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import tyler.discjockey.command.DJCommand;
import tyler.discjockey.command.DiscJockeyCommand;
import tyler.discjockey.module.DiscJockeyModule;
import tyler.discjockey.utils.SongLoader;
import org.jline.utils.Log;

@Plugin(
    id = "disc-jockey-plugin",
    version = BuildConstants.VERSION,
    description = "Port of the Disc Jockey Mod to Zenith Proxy",
    url = "https://github.com/rfresh2/ZenithProxyExamplePlugin",
    authors = {"tyler"}
)
public class DiscJockeyPlugin implements ZenithProxyPlugin {
    // public static for simple access from modules and commands
    // or alternatively, you could pass these around in constructors
    public static ExampleConfig PLUGIN_CONFIG;
    public static ComponentLogger LOG;

    @Override
    public void onLoad(PluginAPI pluginAPI) {
        LOG = pluginAPI.getLogger();
        LOG.info("Disc Jockey Plugin loading test test");
        // initialize any configurations before modules or commands might need to read them
        PLUGIN_CONFIG = pluginAPI.registerConfig("disc-jockey-plugin", ExampleConfig.class);

        SongLoader.loadSongs();
        pluginAPI.registerCommand(new DJCommand());
        pluginAPI.registerCommand(new DiscJockeyCommand());
        pluginAPI.registerModule(new DiscJockeyModule());
        LOG.info("Disc Jockey Plugin loaded!");
    }
}
