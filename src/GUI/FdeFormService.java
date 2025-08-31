package GUI;

import java.io.Closeable;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A large demonstration class that simulates a "Fast Data Entry" workflow service.
 * <p>
 * This class intentionally includes a wide spectrum of Java language features:
 * <ul>
 *   <li>Sealed hierarchies (Java 17)</li>
 *   <li>Records</li>
 *   <li>Builder pattern</li>
 *   <li>Generics & functional utilities</li>
 *   <li>Concurrency with ExecutorService & ScheduledExecutorService</li>
 *   <li>In-memory caching & simple LRU</li>
 *   <li>Validation & Result type</li>
 *   <li>Mini JSON-like serialization (no external libs)</li>
 *   <li>Streaming pipelines & pattern matching (instanceof)</li>
 * </ul>
 * <p>
 * NOTE: This is a self-contained example meant for learning/demo purposes.
 */
public class FdeFormService implements Closeable {

    // ---- Logging -------------------------------------------------------------------------
    private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

    // ---- Domain --------------------------------------------------------------------------
    public enum EngineType { RFE, AFE }

    public enum Priority { LOW, NORMAL, HIGH, CRITICAL }

    /** Simple value object for a question/answer pair */
    public record QA(String questionId, String answer) {
        public QA {
            if (questionId == null || questionId.isBlank()) {
                throw new IllegalArgumentException("questionId cannot be null/blank");
            }
        }
    }

    /** A form submission payload */
    public record FormSubmission(
            UUID id,
            String patientId,
            String formUuid,
            EngineType engine,
            Priority priority,
            Instant createdAt,
            List<QA> responses
    ) {
        public FormSubmission {
            Objects.requireNonNull(id);
            Objects.requireNonNull(engine);
            Objects.requireNonNull(priority);
            Objects.requireNonNull(createdAt);
            responses = List.copyOf(responses == null ? List.of() : responses);
        }

        public String toJson() {
            // Tiny hand-rolled serializer for demo
            String qas = responses.stream()
                    .map(qa -> "{\"questionId\":\"" + escape(qa.questionId()) + "\",\"answer\":\"" + escape(opt(qa.answer())) + "\"}")
                    .collect(Collectors.joining(","));
            return """
                   {
                     "id":"%s",
                     "patientId":"%s",
                     "formUuid":"%s",
                     "engine":"%s",
                     "priority":"%s",
                     "createdAt":"%s",
                     "responses":[%s]
                   }
                   """.formatted(
                    id, escape(opt(patientId)), escape(opt(formUuid)), engine, priority,
                    createdAt.toString(), qas);
        }

        private static String opt(String s) { return s == null ? "" : s; }
        private static String escape(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    /** Minimal validation error */
    public record Violation(String field, String message) {}

    /** A simple sum type for success/failure results */
    public sealed interface Result<T> permits Result.Ok, Result.Err {
        record Ok<T>(T value) implements Result<T> {}
        record Err<T>(List<Violation> violations) implements Result<T> {}
        static <T> Ok<T> ok(T v) { return new Ok<>(v); }
        static <T> Err<T> err(List<Violation> v) { return new Err<>(List.copyOf(v)); }
    }

    /** A sealed hierarchy for processing commands */
    public sealed interface Command permits Submit, Requeue, Cancel {
        UUID submissionId();
        Priority priority();
    }
    public record Submit(FormSubmission submission) implements Command {
        public UUID submissionId() { return submission.id(); }
        public Priority priority() { return submission.priority(); }
    }
    public record Requeue(UUID submissionId, Priority priority, String reason) implements Command {}
    public record Cancel(UUID submissionId, Priority priority, String reason) implements Command {}

    // ---- Configuration + Builder ----------------------------------------------------------
    public static final class Config {
        private final EngineType engine;
        private final int workerThreads;
        private final Duration submitTimeout;
        private final ZoneId zone;
        private final int cacheSize;

        private Config(Builder b) {
            this.engine = b.engine;
            this.workerThreads = b.workerThreads;
            this.submitTimeout = b.submitTimeout;
            this.zone = b.zone;
            this.cacheSize = b.cacheSize;
        }

        public EngineType engine() { return engine; }
        public int workerThreads() { return workerThreads; }
        public Duration submitTimeout() { return submitTimeout; }
        public ZoneId zone() { return zone; }
        public int cacheSize() { return cacheSize; }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private EngineType engine = EngineType.RFE;
            private int workerThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
            private Duration submitTimeout = Duration.ofSeconds(10);
            private ZoneId zone = ZoneId.systemDefault();
            private int cacheSize = 256;

            public Builder engine(EngineType e) { this.engine = Objects.requireNonNull(e); return this; }
            public Builder workerThreads(int n) { this.workerThreads = Math.max(1, n); return this; }
            public Builder submitTimeout(Duration d) { this.submitTimeout = Objects.requireNonNull(d); return this; }
            public Builder zone(ZoneId z) { this.zone = Objects.requireNonNull(z); return this; }
            public Builder cacheSize(int size) { this.cacheSize = Math.max(32, size); return this; }
            public Config build() { return new Config(this); }
        }
    }

    // ---- State & Infra -------------------------------------------------------------------
    private final Config config;
    private final ExecutorService workers;
    private final ScheduledExecutorService scheduler;
    private final Map<UUID, FormSubmission> store = new ConcurrentHashMap<>();
    private final Queue<Command> queue = new ConcurrentLinkedQueue<>();
    private final MiniLruCache<UUID, String> renderCache;
    private final Phaser drainPhaser = new Phaser(1); // registered by service; workers register while running

    private final Map<Priority, Integer> processedCount = new EnumMap<>(Priority.class);

    // ---- Construction --------------------------------------------------------------------
    public FdeFormService(Config config) {
        this.config = Objects.requireNonNull(config);
        this.workers = Executors.newFixedThreadPool(config.workerThreads());
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.renderCache = new MiniLruCache<>(config.cacheSize());
        for (Priority p : Priority.values()) processedCount.put(p, 0);

        // periodic metrics log
        scheduler.scheduleAtFixedRate(this::logMetrics, 2, 5, TimeUnit.SECONDS);
    }

    // ---- API -----------------------------------------------------------------------------
    /** Validate and accept a submission, returning its id or violations. */
    public Result<UUID> accept(FormSubmission submission) {
        List<Violation> v = validate(submission);
        if (!v.isEmpty()) return Result.err(v);

        // Persist & enqueue
        store.put(submission.id(), submission);
        queue.add(new Submit(submission));
        return Result.ok(submission.id());
    }

    /** Requeue a submission with a new priority. */
    public Result<UUID> requeue(UUID id, Priority newPriority, String reason) {
        if (!store.containsKey(id)) return Result.err(List.of(new Violation("id", "Unknown submission")));
        queue.add(new Requeue(id, newPriority, reason));
        return Result.ok(id);
    }

    /** Cancel a submission if present. */
    public Result<UUID> cancel(UUID id, String reason) {
        if (!store.containsKey(id)) return Result.err(List.of(new Violation("id", "Unknown submission")));
        queue.add(new Cancel(id, Priority.HIGH, reason));
        return Result.ok(id);
    }

    /** Starts background workers that drain the queue. */
    public void start() {
        for (int i = 0; i < config.workerThreads(); i++) {
            final int workerId = i + 1;
            workers.submit(() -> drainLoop("W" + workerId));
        }
    }

    /** Render a human-friendly preview of a submission, cache the result. */
    public String render(UUID id) {
        String cached = renderCache.get(id);
        if (cached != null) return cached;

        FormSubmission s = store.get(id);
        if (s == null) return "<missing>";
        String body = s.responses().stream()
                .sorted(Comparator.comparing(QA::questionId))
                .map(q -> "- " + q.questionId() + " → " + (q.answer() == null || q.answer().isBlank() ? "<empty>" : q.answer()))
                .collect(Collectors.joining("\n"));

        String header = "[%s] %s  (%s, %s)".formatted(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(s.createdAt().atZone(config.zone())),
                s.patientId(), s.engine(), s.priority());

        String rendered = header + "\n" + body;
        renderCache.put(id, rendered);
        return rendered;
    }

    /** Basic export as JSON (string). */
    public String exportJson(Collection<UUID> ids) {
        StringJoiner join = new StringJoiner(",", "[", "]");
        ids.stream()
                .map(store::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(FormSubmission::createdAt))
                .forEach(s -> join.add(s.toJson()));
        return join.toString();
    }

    /** Stats snapshot. */
    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("engine", config.engine().name());
        m.put("queueSize", queue.size());
        m.put("stored", store.size());
        m.put("processedByPriority", new EnumMap<>(processedCount));
        return Collections.unmodifiableMap(m);
    }

    // ---- Internals -----------------------------------------------------------------------
    private void drainLoop(String tag) {
        drainPhaser.register();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Command cmd = queue.poll();
                if (cmd == null) {
                    sleepQuietly(50);
                    continue;
                }
                try {
                    switch (cmd) {
                        case Submit s -> processSubmit(tag, s.submission());
                        case Requeue r -> processRequeue(tag, r);
                        case Cancel c -> processCancel(tag, c);
                    }
                } catch (Throwable t) {
                    LOG.log(Level.SEVERE, tag + " failed to handle " + cmd.getClass().getSimpleName(), t);
                }
            }
        } finally {
            drainPhaser.arriveAndDeregister();
        }
    }

    private void processSubmit(String tag, FormSubmission s) {
        LOG.info(() -> tag + " SUBMIT " + s.id() + " " + s.priority());
        // simulate I/O via futures with timeout
        CompletableFuture<Void> fut = CompletableFuture
                .supplyAsync(() -> validate(s), workers)
                .thenAccept(v -> {
                    if (!v.isEmpty()) throw new CompletionException(new IllegalStateException("Validation failed"));
                })
                .thenRun(() -> simulatePersist(s))
                .orTimeout(config.submitTimeout().toMillis(), TimeUnit.MILLISECONDS);

        try {
            fut.join();
            bumpProcessed(s.priority());
            renderCache.remove(s.id()); // drop stale preview
        } catch (CompletionException ex) {
            LOG.log(Level.WARNING, tag + " submit failed: " + s.id(), ex.getCause());
            // requeue with lower priority to avoid hot-looping
            queue.add(new Requeue(s.id(), Priority.LOW, "Auto requeue after failure"));
        }
    }

    private void processRequeue(String tag, Requeue r) {
        LOG.info(() -> tag + " REQUEUE " + r.submissionId() + " -> " + r.priority() + " (" + r.reason() + ")");
        FormSubmission s = store.get(r.submissionId());
        if (s == null) return;
        FormSubmission updated = new FormSubmission(
                s.id(), s.patientId(), s.formUuid(), s.engine(), r.priority(), s.createdAt(), s.responses());
        store.put(s.id(), updated);
        queue.add(new Submit(updated));
    }

    private void processCancel(String tag, Cancel c) {
        LOG.info(() -> tag + " CANCEL " + c.submissionId() + " (" + c.reason() + ")");
        store.remove(c.submissionId());
        renderCache.remove(c.submissionId());
    }

    private void simulatePersist(FormSubmission s) {
        // simulate a small random latency and occasional transient failure
        sleepQuietly(20 + new Random().nextInt(40));
        if (new Random().nextDouble() < 0.02) {
            throw new RuntimeException("Transient DB error");
        }
    }

    private void bumpProcessed(Priority p) {
        processedCount.compute(p, (k, v) -> v == null ? 1 : v + 1);
    }

    private List<Violation> validate(FormSubmission s) {
        List<Violation> v = new ArrayList<>();
        if (s.patientId() == null || s.patientId().isBlank())
            v.add(new Violation("patientId", "required"));
        if (s.formUuid() == null || s.formUuid().isBlank())
            v.add(new Violation("formUuid", "required"));
        if (s.engine() != config.engine())
            v.add(new Violation("engine", "Service is configured for " + config.engine() + " but got " + s.engine()));
        Set<String> seen = new HashSet<>();
        for (QA qa : s.responses()) {
            if (!seen.add(qa.questionId())) {
                v.add(new Violation("responses[" + qa.questionId() + "]", "duplicate questionId"));
            }
        }
        return v;
    }

    private void logMetrics() {
        Map<String, Object> s = stats();
        LOG.info(() -> "[metrics] " + s);
    }

    private static void sleepQuietly(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    // ---- Mini LRU Cache ------------------------------------------------------------------
    public static final class MiniLruCache<K, V> {
        private final int capacity;
        private final Map<K, V> map = new HashMap<>();
        private final ArrayDeque<K> order = new ArrayDeque<>();

        public MiniLruCache(int capacity) { this.capacity = capacity; }

        public synchronized void put(K k, V v) {
            if (map.containsKey(k)) order.remove(k);
            map.put(k, v);
            order.addFirst(k);
            if (map.size() > capacity) {
                K last = order.removeLast();
                map.remove(last);
            }
        }

        public synchronized V get(K k) {
            V v = map.get(k);
            if (v != null) {
                order.remove(k);
                order.addFirst(k);
            }
            return v;
        }

        public synchronized void remove(K k) {
            map.remove(k);
            order.remove(k);
        }

        public synchronized int size() { return map.size(); }
    }

    // ---- Utility: small functional helpers ----------------------------------------------
    public static <T, K> Map<K, List<T>> groupBy(Collection<T> items, Function<T, K> keyFn) {
        return items.stream().collect(Collectors.groupingBy(keyFn, LinkedHashMap::new, Collectors.toList()));
    }

    public static OptionalInt indexOfFirst(List<String> list, String prefix) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).startsWith(prefix)) return OptionalInt.of(i);
        }
        return OptionalInt.empty();
    }

    // ---- Closeable -----------------------------------------------------------------------
    @Override
    public void close() {
        try {
            scheduler.shutdownNow();
            workers.shutdownNow();
            drainPhaser.arriveAndDeregister();
            workers.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- Demo main -----------------------------------------------------------------------
    public static void main(String[] args) throws IOException {
        Config cfg = Config.builder()
                .engine(EngineType.RFE)
                .workerThreads(4)
                .submitTimeout(Duration.ofSeconds(2))
                .zone(ZoneId.of("Africa/Kampala"))
                .cacheSize(128)
                .build();

        try (FdeFormService svc = new FdeFormService(cfg)) {
            svc.start();

            // create some submissions
            List<FormSubmission> forms = List.of(
                    new FormSubmission(
                            UUID.randomUUID(), "patient-001", "form-OBS-001",
                            EngineType.RFE, Priority.NORMAL, Instant.now(),
                            List.of(new QA("nhifStatus", "Active"),
                                    new QA("SOAPObjectiveFindings", "BP:120/80"))),
                    new FormSubmission(
                            UUID.randomUUID(), "patient-002", "form-OBS-002",
                            EngineType.RFE, Priority.HIGH, Instant.now(),
                            List.of(new QA("scheduledVisit", "2025-09-05"),
                                    new QA("nhifStatus", "Inactive"))),
                    new FormSubmission(
                            UUID.randomUUID(), "patient-003", "form-OBS-003",
                            EngineType.RFE, Priority.CRITICAL, Instant.now(),
                            List.of(new QA("triageScore", "7"),
                                    new QA("nhifStatus", "Active")))
            );

            // accept them and print results
            for (FormSubmission fs : forms) {
                Result<UUID> r = svc.accept(fs);
                switch (r) {
                    case Result.Ok<UUID> ok -> System.out.println("Accepted: " + ok.value());
                    case Result.Err<UUID> err -> System.out.println("Rejected: " + err.violations());
                }
            }

            // render previews
            System.out.println("\n--- Renders ---");
            forms.forEach(f -> System.out.println(svc.render(f.id()) + "\n"));

            // export JSON
            String json = svc.exportJson(forms.stream().map(FormSubmission::id).toList());
            System.out.println("--- Export JSON ---");
            System.out.println(json);

            // demonstrate requeue + cancel
            svc.requeue(forms.get(0).id(), Priority.HIGH, "Manual boost");
            svc.cancel(forms.get(1).id(), "User aborted");

            // keep the demo running briefly to see metrics & processing
            sleepQuietly(3500);

            System.out.println("\n--- Stats ---");
            System.out.println(svc.stats());
        }
    }
}

