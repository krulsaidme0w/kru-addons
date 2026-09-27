package dev.krul.cobblemon.observation;

import java.util.*;

/** Client-thread shared collector. Snapshots contain no live objects. */
public final class ObservationRuntime {
    public enum State { NO_WORLD, SCANNING, LIVE, READER_UNSUPPORTED }
    public record Entry(ObservationSnapshot observation, int entityId) {}
    public record View(ObservationSnapshot.Scope scope, State state, List<Entry> entries) {
        public View { entries = List.copyOf(entries); }
    }
    public interface Cursor {
        boolean complete();
        /** One source unit, including empty chunk slots; null emits no observation. */
        Entry next(long now);
    }
    public interface Source {
        Cursor open(ObservationSnapshot.Scope scope, double radius);
        boolean loaded(Entry entry);
        Entry inspect(Entry entry, long now);
    }
    public final class Subscription implements AutoCloseable {
        private final String consumer;
        private double radius;
        private View view = new View(null, State.NO_WORLD, List.of());
        private Subscription(String consumer, double radius) { this.consumer = consumer; this.radius = checkedRadius(radius); }
        public void radius(double value) {
            value = checkedRadius(value);
            if (radius != value) { radius = value; cursor = null; }
        }
        public View snapshot() { return view; }
        public Optional<Entry> inspect(ObservationSnapshot.Identity identity, long now) {
            if (!subscriptions.containsValue(this) || !Objects.equals(scope, identity.scope()) || source == null) return Optional.empty();
            Entry entry = entries.get(identity);
            if (entry == null || !within(entry, radius) || !source.loaded(entry)) return Optional.empty();
            Entry detail = source.inspect(entry, now);
            return detail != null && identity.equals(detail.observation().identity()) ? Optional.of(detail) : Optional.empty();
        }
        public void close() {
            subscriptions.remove(consumer, this);
            view = new View(null, State.NO_WORLD, List.of());
            if (subscriptions.isEmpty()) { entries.clear(); cursor = null; }
        }
    }
    private final int budget, capacity;
    private final Map<String, Subscription> subscriptions = new LinkedHashMap<>();
    private final Map<ObservationSnapshot.Identity, Entry> entries = new LinkedHashMap<>();
    private Object connection, world;
    private ObservationSnapshot.Scope scope;
    private Source source;
    private Cursor cursor;
    public ObservationRuntime(int budget, int capacity) {
        if (budget < 1 || budget > 256 || capacity < 1 || capacity > 2048) throw new IllegalArgumentException("Invalid bounds");
        this.budget = budget; this.capacity = capacity;
    }
    public Subscription subscribe(String consumer, double radius) {
        if (consumer == null || consumer.isBlank() || subscriptions.containsKey(consumer)) throw new IllegalArgumentException("Unique consumer required");
        var subscription = new Subscription(consumer, radius);
        subscriptions.put(consumer, subscription); cursor = null;
        return subscription;
    }
    public void tick(Object connection, Object world, Source source, long now) {
        if (this.connection != connection || this.world != world) {
            UUID connectionId = this.connection == connection && scope != null ? scope.connectionId() : UUID.randomUUID();
            this.connection = connection; this.world = world;
            scope = connection == null || world == null ? null : new ObservationSnapshot.Scope(connectionId, UUID.randomUUID());
            entries.clear(); cursor = null;
        }
        this.source = source;
        State state = State.NO_WORLD;
        if (scope != null && !subscriptions.isEmpty()) {
            try {
                double radius = subscriptions.values().stream().mapToDouble(s -> s.radius).max().orElse(1);
                if (cursor == null || cursor.complete()) cursor = source.open(scope, radius);
                for (int i = 0; i < budget && !cursor.complete(); i++) {
                    Entry entry = cursor.next(now);
                    if (entry == null || !scope.equals(entry.observation().identity().scope()) || !within(entry, radius)) continue;
                    entries.remove(entry.observation().identity());
                    entries.put(entry.observation().identity(), entry);
                    while (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
                }
                // At most capacity cheap liveness checks, separate from the expensive fact-read budget.
                entries.values().removeIf(entry -> !source.loaded(entry) || now - entry.observation().seenAt() > 30_000);
                state = cursor.complete() ? State.LIVE : State.SCANNING;
            } catch (RuntimeException | LinkageError unsupported) {
                cursor = null; entries.clear(); state = State.READER_UNSUPPORTED;
            }
        }
        for (var subscription : subscriptions.values()) {
            double radius = subscription.radius;
            subscription.view = new View(scope, state, entries.values().stream().filter(e -> within(e, radius)).toList());
        }
    }
    private static boolean within(Entry entry, double radius) {
        FactValue distance = entry.observation().facts().get("distance");
        return distance != null && distance.available() && (double) distance.value() <= radius;
    }
    private static double checkedRadius(double radius) {
        if (!Double.isFinite(radius) || radius < 1 || radius > 512) throw new IllegalArgumentException("Radius must be 1–512");
        return radius;
    }
}
