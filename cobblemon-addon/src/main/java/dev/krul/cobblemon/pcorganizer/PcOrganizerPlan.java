package dev.krul.cobblemon.pcorganizer;

import java.util.*;

public record PcOrganizerPlan(List<Move> moves, int selectedCount, int protectedCount, Set<Integer> touchedBoxes) {
    public PcOrganizerPlan {
        moves = moves == null ? List.of() : List.copyOf(moves);
        touchedBoxes = touchedBoxes == null ? Set.of() : Set.copyOf(touchedBoxes);
    }
    public record Move(PcOrganizerPokemon pokemon, int fromBox, int fromSlot, int toBox, int toSlot) {
        public Move {
            Objects.requireNonNull(pokemon, "pokemon");
            if (fromBox < 0 || fromSlot < 0 || toBox < 0 || toSlot < 0) throw new IllegalArgumentException("negative move position");
        }
    }
}
