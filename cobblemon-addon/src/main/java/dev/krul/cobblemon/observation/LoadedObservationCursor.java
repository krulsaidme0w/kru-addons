package dev.krul.cobblemon.observation;
import java.util.*;
import java.util.function.*;

/** Lazy loaded-slot mechanics extracted from DungeonWorldProbe, without eager backing copies. */
public final class LoadedObservationCursor<E, B> implements ObservationRuntime.Cursor {
    private final Iterator<E> entities;
    private final int slots;
    private final IntFunction<Iterator<B>> blocks;
    private final BiFunction<E, Long, ObservationRuntime.Entry> entityReader;
    private final BiFunction<B, Long, ObservationRuntime.Entry> blockReader;
    private Iterator<B> current = Collections.emptyIterator();
    private int slot;
    public LoadedObservationCursor(Iterable<E> entities, int slots, IntFunction<Iterator<B>> blocks,
                                   BiFunction<E, Long, ObservationRuntime.Entry> entityReader,
                                   BiFunction<B, Long, ObservationRuntime.Entry> blockReader) {
        this.entities = entities.iterator(); this.slots = slots; this.blocks = blocks;
        this.entityReader = entityReader; this.blockReader = blockReader;
    }
    public boolean complete() { return !entities.hasNext() && slot >= slots && !current.hasNext(); }
    public ObservationRuntime.Entry next(long now) {
        if (entities.hasNext()) return entityReader.apply(entities.next(), now);
        if (current.hasNext()) return blockReader.apply(current.next(), now);
        if (slot < slots) current = blocks.apply(slot++);
        return null;
    }
}
