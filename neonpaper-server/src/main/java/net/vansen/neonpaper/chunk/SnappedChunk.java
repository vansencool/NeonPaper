package net.vansen.neonpaper.chunk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.Arrays;

public record SnappedChunk(SnappedChunk.SnappedSectionData[] data) {
    public static final Codec<SnappedChunk> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        SnappedSectionData.CODEC.listOf().fieldOf("data")
            .xmap(list -> list.toArray(SnappedSectionData[]::new), Arrays::asList)
            .forGetter(SnappedChunk::data)
    ).apply(instance, SnappedChunk::new));


    /**
     * Copy the snapped chunk.
     *
     * @return a copy of the snapped chunk
     */
    public SnappedChunk copy() {
        SnappedSectionData[] data = new SnappedSectionData[this.data.length];
        for (int i = 0; i < this.data.length; i++) {
            data[i] = new SnappedSectionData(this.data[i].nonEmptyBlockCount, this.data[i].tickingBlockCount,
                this.data[i].tickingFluidCount, this.data[i].specialCollidingBlocks,
                this.data[i].states.copy());
        }
        return new SnappedChunk(data);
    }

    public record SnappedSectionData(
        short nonEmptyBlockCount,
        short tickingBlockCount,
        short tickingFluidCount,
        short specialCollidingBlocks,
        PalettedContainer<BlockState> states
    ) {
        public static final Codec<PalettedContainer<BlockState>> BLOCKSTATE_CONTAINER_CODEC =
            PalettedContainer.codecRO(
                Block.BLOCK_STATE_REGISTRY,
                BlockState.CODEC,
                PalettedContainer.Strategy.SECTION_STATES,
                Blocks.AIR.defaultBlockState()
            ).xmap(
                c -> (PalettedContainer<BlockState>) c,
                c -> c
            );

        public static final Codec<SnappedSectionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.SHORT.fieldOf("nonEmptyBlockCount").forGetter(SnappedSectionData::nonEmptyBlockCount),
                Codec.SHORT.fieldOf("tickingBlockCount").forGetter(SnappedSectionData::tickingBlockCount),
                Codec.SHORT.fieldOf("tickingFluidCount").forGetter(SnappedSectionData::tickingFluidCount),
                Codec.INT.fieldOf("specialCollidingBlocks") // backwards capability, stored as an int, used as a short in runtime
                        .xmap(i -> (short) i.intValue(), s -> (int) s)
                        .forGetter(SnappedSectionData::specialCollidingBlocks),
                BLOCKSTATE_CONTAINER_CODEC.fieldOf("states").forGetter(SnappedSectionData::states)
        ).apply(instance, SnappedSectionData::new));
    }
}
