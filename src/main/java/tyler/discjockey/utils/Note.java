package tyler.discjockey.utils;

import com.zenith.mc.block.Block;
import com.zenith.mc.block.BlockRegistry;
import com.zenith.mc.block.properties.NoteBlockInstrument;

import java.util.HashMap;

public record Note(NoteBlockInstrument instrument, byte note) {
    public static final HashMap<NoteBlockInstrument, Block> INSTRUMENT_BLOCKS = new HashMap<>();

    public static final byte LAYER_SHIFT = Short.SIZE;
    public static final byte INSTRUMENT_SHIFT = Short.SIZE * 2;
    public static final byte NOTE_SHIFT = Short.SIZE * 2 + Byte.SIZE;

    public static final NoteBlockInstrument[] INSTRUMENTS = new NoteBlockInstrument[]{
            NoteBlockInstrument.HARP,
            NoteBlockInstrument.BASS,
            NoteBlockInstrument.BASEDRUM,
            NoteBlockInstrument.SNARE,
            NoteBlockInstrument.HAT,
            NoteBlockInstrument.GUITAR,
            NoteBlockInstrument.FLUTE,
            NoteBlockInstrument.BELL,
            NoteBlockInstrument.CHIME,
            NoteBlockInstrument.XYLOPHONE,
            NoteBlockInstrument.IRON_XYLOPHONE,
            NoteBlockInstrument.COW_BELL,
            NoteBlockInstrument.DIDGERIDOO,
            NoteBlockInstrument.BIT,
            NoteBlockInstrument.BANJO,
            NoteBlockInstrument.PLING

    };

    static {
        INSTRUMENT_BLOCKS.put(NoteBlockInstrument.HARP, BlockRegistry.AIR);
        INSTRUMENT_BLOCKS.put(NoteBlockInstrument.BASEDRUM, BlockRegistry.STONE);
        INSTRUMENT_BLOCKS.put(NoteBlockInstrument.SNARE, BlockRegistry.SAND);
        INSTRUMENT_BLOCKS.put(NoteBlockInstrument.HAT, BlockRegistry.GLASS);
        INSTRUMENT_BLOCKS.put(NoteBlockInstrument.BASS, BlockRegistry.OAK_PLANKS);
        INSTRUMENT_BLOCKS.put(NoteBlockInstrument.FLUTE, BlockRegistry.CLAY);
        INSTRUMENT_BLOCKS.put(NoteBlockInstrument.BELL, BlockRegistry.GOLD_BLOCK);
        INSTRUMENT_BLOCKS.put(NoteBlockInstrument.GUITAR, BlockRegistry.WHITE_WOOL);
        INSTRUMENT_BLOCKS.put(NoteBlockInstrument.CHIME, BlockRegistry.PACKED_ICE);
        INSTRUMENT_BLOCKS.put(NoteBlockInstrument.XYLOPHONE, BlockRegistry.BONE_BLOCK);
        INSTRUMENT_BLOCKS.put(NoteBlockInstrument.IRON_XYLOPHONE, BlockRegistry.IRON_BLOCK);
        INSTRUMENT_BLOCKS.put(NoteBlockInstrument.COW_BELL, BlockRegistry.SOUL_SAND);
        INSTRUMENT_BLOCKS.put(NoteBlockInstrument.DIDGERIDOO, BlockRegistry.PUMPKIN);
        INSTRUMENT_BLOCKS.put(NoteBlockInstrument.BIT, BlockRegistry.EMERALD_BLOCK);
        INSTRUMENT_BLOCKS.put(NoteBlockInstrument.BANJO, BlockRegistry.HAY_BLOCK);
        INSTRUMENT_BLOCKS.put(NoteBlockInstrument.PLING, BlockRegistry.GLOWSTONE);
    }
}
