package com.example.locateleagcy.locate;

import java.lang.reflect.Field;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderGenerate;
import net.minecraft.world.gen.structure.MapGenStructure;

public class StructureResolver {

    public static MapGenStructure getStructure(World world, String name) {

        try {

            IChunkProvider provider = world.getChunkProvider();

            if (!(provider instanceof ChunkProviderGenerate)) {
                return null;
            }

            ChunkProviderGenerate generate = (ChunkProviderGenerate) provider;

            Field[] fields = ChunkProviderGenerate.class.getDeclaredFields();

            for (Field field : fields) {

                field.setAccessible(true);

                Object value = field.get(generate);

                if (value instanceof MapGenStructure) {

                    MapGenStructure structure = (MapGenStructure) value;

                    if (structure.getClass()
                        .getSimpleName()
                        .toLowerCase()
                        .contains(name.toLowerCase())) {

                        return structure;
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
