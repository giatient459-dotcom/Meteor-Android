/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.events.world;

import net.minecraft.world.level.chunk.LevelChunk;

/**
 * @author Crosby
 * Avoid putting this event in a {@link meteordevelopment.meteorclient.utils.misc.Pool} to prevent a race condition, or in a {@link ThreadLocal}, because it is shared between threads.
 */
public record ChunkDataEvent(LevelChunk chunk) {
}
