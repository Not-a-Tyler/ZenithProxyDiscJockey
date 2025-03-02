package org.example.command;

import com.google.common.collect.Lists;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.Command;
import com.zenith.command.CommandUsage;
import com.zenith.command.brigadier.CommandCategory;
import com.zenith.command.brigadier.CommandContext;
import org.example.ExamplePlugin;
import org.example.module.ExampleESPModule;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;

import static com.zenith.Shared.CACHE;
import static com.zenith.Shared.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class ExampleESPCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("esp")
            .category(CommandCategory.MODULE)
            .description("Renders the spectral effect around all entities")
            .usageLines("on/off")
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("esp")
            .then(argument("toggle", toggle()).executes(c -> {
                ExamplePlugin.PLUGIN_CONFIG.esp = getToggle(c, "toggle");
                // make sure to sync so the module is actually toggled
                MODULE.get(ExampleESPModule.class).syncEnabledFromConfig();
                var player = Proxy.getInstance().getActivePlayer();
                if (player != null) {
                    // resend all entity metadata from cache
                    CACHE.getEntityCache().getEntities().values().forEach(e -> {
                        EntityMetadata<?, ?> toSend;
                        toSend = e.getMetadata().get(0);
                        if (toSend == null)
                            toSend = new ByteEntityMetadata(0, MetadataType.BYTE, (byte) 0);
                        player.sendAsync(new ClientboundSetEntityDataPacket(e.getEntityId(), Lists.newArrayList(toSend)));
                    });
                }
                c.getSource().getEmbed()
                    .title("ESP " + toggleStrCaps(ExamplePlugin.PLUGIN_CONFIG.esp))
                    .primaryColor();
                return 1;
            }));
    }
}
