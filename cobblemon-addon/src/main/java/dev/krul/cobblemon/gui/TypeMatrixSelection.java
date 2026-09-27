package dev.krul.cobblemon.gui;

import dev.krul.cobblemon.battle.PokemonType;

import java.util.Optional;

public final class TypeMatrixSelection {
    private double headerWidth;
    private double headerHeight;
    private double cellWidth;
    private double cellHeight;
    private PokemonType hoveredAttack;
    private PokemonType hoveredDefend;
    private PokemonType lockedAttack;
    private PokemonType lockedDefend;
    private boolean locked;

    public TypeMatrixSelection(double headerWidth, double headerHeight,
                               double cellWidth, double cellHeight) {
        resize(headerWidth, headerHeight, cellWidth, cellHeight);
    }

    public void resize(double headerWidth, double headerHeight,
                       double cellWidth, double cellHeight) {
        if (headerWidth <= 0 || headerHeight <= 0 || cellWidth <= 0 || cellHeight <= 0) {
            throw new IllegalArgumentException("matrix sizes must be positive");
        }
        this.headerWidth = headerWidth;
        this.headerHeight = headerHeight;
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
    }

    public void hover(double localX, double localY) {
        hoveredAttack = typeAt(localY, headerHeight, cellHeight);
        hoveredDefend = typeAt(localX, headerWidth, cellWidth);
        if (localX < headerWidth) hoveredDefend = null;
        if (localY < headerHeight) hoveredAttack = null;
        double width = headerWidth + PokemonType.values().length * cellWidth;
        double height = headerHeight + PokemonType.values().length * cellHeight;
        if (localX < 0 || localX >= width || localY < 0 || localY >= height) {
            hoveredAttack = null;
            hoveredDefend = null;
        }
    }

    private PokemonType typeAt(double coordinate, double header, double cell) {
        int index = (int) ((coordinate - header) / cell);
        return index >= 0 && index < PokemonType.values().length ? PokemonType.values()[index] : null;
    }

    public void clickCell(PokemonType attack, PokemonType defend) {
        if (locked && lockedAttack == attack && lockedDefend == defend) {
            clear();
            return;
        }
        locked = true;
        lockedAttack = attack;
        lockedDefend = defend;
    }

    public void clickAttackHeader(PokemonType attack) {
        if (locked && lockedAttack == attack && lockedDefend == null) {
            clear();
            return;
        }
        locked = true;
        lockedAttack = attack;
        lockedDefend = null;
    }

    public void clickDefendHeader(PokemonType defend) {
        if (locked && lockedAttack == null && lockedDefend == defend) {
            clear();
            return;
        }
        locked = true;
        lockedAttack = null;
        lockedDefend = defend;
    }

    public void clear() {
        locked = false;
        lockedAttack = null;
        lockedDefend = null;
    }

    public boolean locked() { return locked; }
    public Optional<PokemonType> hoveredAttack() { return Optional.ofNullable(hoveredAttack); }
    public Optional<PokemonType> hoveredDefend() { return Optional.ofNullable(hoveredDefend); }
    public Optional<PokemonType> activeAttack() {
        return Optional.ofNullable(locked ? lockedAttack : hoveredAttack);
    }
    public Optional<PokemonType> activeDefend() {
        return Optional.ofNullable(locked ? lockedDefend : hoveredDefend);
    }
}

