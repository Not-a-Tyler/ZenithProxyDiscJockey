package org.example.module;

import com.zenith.module.Module;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.registry.PacketHandlerCodec;
import com.zenith.network.registry.PacketHandlerStateCodec;
import com.zenith.network.server.ServerSession;
import org.example.ExamplePlugin;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;

import java.util.ArrayList;

public class ExampleESPModule extends Module {
    @Override
    public boolean enabledSetting() {
        return ExamplePlugin.PLUGIN_CONFIG.esp;
    }

    @Override
    public PacketHandlerCodec registerServerPacketHandlerCodec() {
        return PacketHandlerCodec.builder()
            .setId("esp")
            .setPriority(1000)
            .state(ProtocolState.GAME, PacketHandlerStateCodec.<ServerSession>builder()
                // packet classes can and will change between MC versions
                // if you want to have packet handlers you probably need separate plugin builds for each MC version
                .registerInbound(ClientboundSetEntityDataPacket.class, new GlowingEntityMetadataPacketHandler())
                // or with in-line lambda:
//                .registerOutbound(ClientboundSetEntityDataPacket.class, (ClientboundSetEntityDataPacket packet, ServerSession session) -> {
//                    ClientboundSetEntityDataPacket p = packet;
//                    ...more impl...
//                    return p;
//                })
                // beware there are different PacketHandler interfaces that would be trickier to declare as a lambda
                // i.e. to handle client packets on the tick loop you need to implement ClientEventLoopPacketHandler
                .build())
            .build();
    }


    // this can also be moved to a separate class file
    public static class GlowingEntityMetadataPacketHandler implements PacketHandler<ClientboundSetEntityDataPacket, ServerSession> {
        @Override
        public ClientboundSetEntityDataPacket apply(final ClientboundSetEntityDataPacket packet, final ServerSession session) {
            ClientboundSetEntityDataPacket p = packet;
            var metadata = packet.getMetadata();
            boolean edited = false;
            for (int i = 0; i < metadata.size(); i++) {
                final EntityMetadata<?, ?> entityMetadata = metadata.get(i);
                // https://minecraft.wiki/w/Minecraft_Wiki:Projects/wiki.vg_merge/Entity_metadata#Entity
                if (entityMetadata.getId() == 0 && entityMetadata.getType() == MetadataType.BYTE) {
                    ByteEntityMetadata byteMetadata = (ByteEntityMetadata) entityMetadata;
                    var newMetadata = new ByteEntityMetadata(0, MetadataType.BYTE, (byte) (byteMetadata.getPrimitiveValue() | 0x40));
                    var newMetadataList = new ArrayList<>(metadata);
                    newMetadataList.set(i, newMetadata);
                    p = packet.withMetadata(newMetadataList);
                    edited = true;
                    break;
                }
            }
            if (!edited) {
                var newMetadata = new ArrayList<EntityMetadata<?, ?>>(metadata.size() + 1);
                newMetadata.addAll(packet.getMetadata());
                newMetadata.add(new ByteEntityMetadata(0, MetadataType.BYTE, (byte) 0x40));
                p = packet.withMetadata(newMetadata);
            }
            return p;
        }
    }
}
