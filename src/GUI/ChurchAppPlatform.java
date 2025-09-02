package GUI;

import java.io.Closeable;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * ChurchAppPlatform
 * A large, self-contained demo "platform" class for a church mobile app backend.
 * - Java 17 features: records, sealed classes, switch patterns
 * - Domain: Members, Events, Sermons, Donations, Notifications
 * - Services: Auth (toy JWT), Email/SMS mock, Scheduling, Caching, Exports (CSV/ICS)
 * - Repos: In-memory with indexing/search
 * - Utilities: Validation, templating, hashing
 *
 * NOTE: This is a demo. Replace mocks with real implementations when wiring to Spring Boot, databases, etc.
 */
public class ChurchAppPlatform implements Closeable {

    // ---- Logging ----------------------------------------------------------------------------
    private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

    // ---- Configuration ----------------------------------------------------------------------
    public static final class Config {
        private final ZoneId zone;
        private final int threads;
        private final int cacheSize;
        private final Duration notificationTick;

        private Config(Builder b) {
            this.zone = b.zone;
            this.threads = b.threads;
            this.cacheSize = b.cacheSize;
            this.notificationTick = b.notificationTick;
        }

        public ZoneId zone() { return zone; }
        public int threads() { return threads; }
        public int cacheSize() { return cacheSize; }
        public Duration notificationTick() { return notificationTick; }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private ZoneId zone = ZoneId.of("Africa/Kampala");
            private int threads = Math.max(4, Runtime.getRuntime().availableProcessors());
            private int cacheSize = 256;
            private Duration notificationTick = Duration.ofSeconds(5);

            public Builder zone(ZoneId z) { zone = Objects.requireNonNull(z); return this; }
            public Builder threads(int n) { threads = Math.max(2, n); return this; }
            public Builder cacheSize(int n) { cacheSize = Math.max(64, n); return this; }
            public Builder notificationTick(Duration d) { notificationTick = Objects.requireNonNull(d); return this; }
            public Config build() { return new Config(this); }
        }
    }

    // ---- Domain -----------------------------------------------------------------------------
    public enum Role { MEMBER, STAFF, ADMIN }
    public enum Channel { EMAIL, SMS, PUSH }
    public enum DonationMethod { CASH, MOBILE_MONEY, CARD }
    public enum Visibility { PUBLIC, PRIVATE }

    public record Member(
            UUID id,
            String fullName,
            String email,
            String phone,
            Role role,
            LocalDate joinedOn,
            boolean active) {

        public Member {
            if (id == null) id = UUID.randomUUID();
            if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("fullName required");
            role = role == null ? Role.MEMBER : role;
            joinedOn = joinedOn == null ? LocalDate.now() : joinedOn;
        }

        public Member withActive(boolean a) { return new Member(id, fullName, email, phone, role, joinedOn, a); }
    }

    public record Event(
            UUID id,
            String title,
            String description,
            LocalDateTime startsAt,
            Duration duration,
            Visibility visibility,
            Set<UUID> attendees) {

        public Event {
            if (id == null) id = UUID.randomUUID();
            if (title == null || title.isBlank()) throw new IllegalArgumentException("title required");
            if (startsAt == null) throw new IllegalArgumentException("startsAt required");
            if (duration == null || duration.isNegative() || duration.isZero()) duration = Duration.ofHours(2);
            visibility = visibility == null ? Visibility.PUBLIC : visibility;
            attendees = attendees == null ? new HashSet<>() : new HashSet<>(attendees);
        }

        public LocalDateTime endsAt() { return startsAt.plus(duration); }
        public boolean isUpcoming(Clock clock) { return startsAt.isAfter(LocalDateTime.now(clock)); }
        public Event addAttendee(UUID memberId) { var set = new HashSet<>(attendees); set.add(memberId); return new Event(id, title, description, startsAt, duration, visibility, set); }
    }

    public record Sermon(
            UUID id,
            String title,
            String preacher,
            LocalDate date,
            String series,
            String passage,
            String mediaUrl) {

        public Sermon {
            if (id == null) id = UUID.randomUUID();
            if (title == null || title.isBlank()) throw new IllegalArgumentException("title required");
            if (preacher == null || preacher.isBlank()) preacher = "Guest";
            date = date == null ? LocalDate.now() : date;
        }
    }

    public record Donation(
            UUID id,
            UUID memberId,
            DonationMethod method,
            long amountCents,
            String currency,
            LocalDateTime createdAt,
            String note) {

        public Donation {
            if (id == null) id = UUID.randomUUID();
            method = method == null ? DonationMethod.CASH : method;
            currency = (currency == null || currency.isBlank()) ? "UGX" : currency;
            createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
            if (amountCents <= 0) throw new IllegalArgumentException("amount must be positive");
        }
    }

    // Notifications
    public sealed interface Notification permits Email, Sms, Push {
        UUID id();
        UUID memberId();
        String subject();
        String body();
        LocalDateTime scheduledAt();
        Channel channel();
    }
    public record Email(UUID id, UUID memberId, String subject, String body, LocalDateTime scheduledAt) implements Notification {
        public Channel channel() { return Channel.EMAIL; }
    }
    public record Sms(UUID id, UUID memberId, String subject, String body, LocalDateTime scheduledAt) implements Notification {
        public Channel channel() { return Channel.SMS; }
    }
    public record Push(UUID id, UUID memberId, String subject, String body, LocalDateTime scheduledAt) implements Notification {
        public Channel channel() { return Channel.PUSH; }
    }

    // ---- Result Type -----------------------------------------------------------------------
    public sealed interface Result<T> permits Ok, Err {}
    public static final class Ok<T> implements Result<T> {
        public final T value; public Ok(T v){ this.value=v; }
        public String toString(){ return "Ok(" + value + ")"; }
    }
    public static final class Err<T> implements Result<T> {
        public final List<String> errors; public Err(List<String> e){ this.errors=List.copyOf(e); }
        public String toString(){ return "Err(" + errors + ")"; }
    }

    // ---- Repositories (in-memory) ----------------------------------------------------------
    public interface Repo<ID, T> {
        T save(T t);
        Optional<T> find(ID id);
        List<T> all();
        void delete(ID id);
    }

    public static final class MemberRepo implements Repo<UUID, Member> {
        private final Map<UUID, Member> store = new ConcurrentHashMap<>();
        private final Map<String, UUID> byEmail = new ConcurrentHashMap<>();
        public Member save(Member m) {
            store.put(m.id(), m);
            if (m.email()!=null && !m.email().isBlank()) byEmail.put(m.email().toLowerCase(Locale.ROOT), m.id());
            return m;
        }
        public Optional<Member> find(UUID id) { return Optional.ofNullable(store.get(id)); }
        public Optional<Member> findByEmail(String email){
            if (email==null) return Optional.empty();
            UUID id = byEmail.get(email.toLowerCase(Locale.ROOT));
            return id==null? Optional.empty() : find(id);
        }
        public List<Member> all() { return new ArrayList<>(store.values()); }
        public void delete(UUID id) { store.remove(id); }
        public List<Member> search(String q){
            if (q==null || q.isBlank()) return all();
            String s = q.toLowerCase(Locale.ROOT);
            return store.values().stream()
                    .filter(m -> m.fullName().toLowerCase(Locale.ROOT).contains(s) ||
                            (m.email()!=null && m.email().toLowerCase(Locale.ROOT).contains(s)) ||
                            (m.phone()!=null && m.phone().contains(s)))
                    .sorted(Comparator.comparing(Member::fullName))
                    .toList();
        }
    }

    public static final class EventRepo implements Repo<UUID, Event> {
        private final Map<UUID, Event> store = new ConcurrentHashMap<>();
        public Event save(Event e){ store.put(e.id(), e); return e; }
        public Optional<Event> find(UUID id){ return Optional.ofNullable(store.get(id)); }
        public List<Event> all(){ return new ArrayList<>(store.values()); }
        public void delete(UUID id){ store.remove(id); }
        public List<Event> upcoming(Clock clock){
            return store.values().stream().filter(e -> e.isUpcoming(clock))
                    .sorted(Comparator.comparing(Event::startsAt))
                    .toList();
        }
    }

    public static final class SermonRepo implements Repo<UUID, Sermon> {
        private final Map<UUID, Sermon> store = new ConcurrentHashMap<>();
        public Sermon save(Sermon s){ store.put(s.id(), s); return s; }
        public Optional<Sermon> find(UUID id){ return Optional.ofNullable(store.get(id)); }
        public List<Sermon> all(){ return new ArrayList<>(store.values()); }
        public void delete(UUID id){ store.remove(id); }
        public List<Sermon> bySeries(String series){
            String k = series==null? "": series.toLowerCase(Locale.ROOT);
            return store.values().stream()
                    .filter(s -> s.series()!=null && s.series().toLowerCase(Locale.ROOT).equals(k))
                    .sorted(Comparator.comparing(Sermon::date).reversed())
                    .toList();
        }
    }

    public static final class DonationRepo implements Repo<UUID, Donation> {
        private final Map<UUID, Donation> store = new ConcurrentHashMap<>();
        public Donation save(Donation d){ store.put(d.id(), d); return d; }
        public Optional<Donation> find(UUID id){ return Optional.ofNullable(store.get(id)); }
        public List<Donation> all(){ return new ArrayList<>(store.values()); }
        public void delete(UUID id){ store.remove(id); }
        public long totalForMember(UUID memberId){
            return store.values().stream().filter(d -> d.memberId().equals(memberId)).mapToLong(Donation::amountCents).sum();
        }
        public Map<String, Long> totalsByCurrency(){
            return store.values().stream()
                    .collect(Collectors.groupingBy(Donation::currency, Collectors.summingLong(Donation::amountCents)));
        }
        public List<Donation> recent(int n){
            return store.values().stream()
                    .sorted(Comparator.comparing(Donation::createdAt).reversed())
                    .limit(n).toList();
        }
    }

    // ---- Services -------------------------------------------------------------------------
    public static final class MiniLru<K,V> {
        private final int cap; private final Map<K,V> m = new HashMap<>(); private final Deque<K> dq = new ArrayDeque<>();
        public MiniLru(int cap){ this.cap = Math.max(32, cap); }
        public synchronized void put(K k, V v){ if(m.containsKey(k)) dq.remove(k); m.put(k,v); dq.addFirst(k); if(m.size()>cap){ K last=dq.removeLast(); m.remove(last);} }
        public synchronized V get(K k){ V v = m.get(k); if(v!=null){ dq.remove(k); dq.addFirst(k);} return v; }
        public synchronized void remove(K k){ m.remove(k); dq.remove(k); }
    }

    public static final class Template {
        public static String render(String tpl, Map<String, Object> ctx){
            String out = tpl;
            for (var e : ctx.entrySet()){
                out = out.replace("{{"+e.getKey()+"}}", String.valueOf(e.getValue()));
            }
            return out;
        }
    }

    public static final class ToyJwt {
        // toy! do NOT use in production
        public static String sign(String subject, Duration ttl){
            String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
            long exp = Instant.now().plus(ttl).getEpochSecond();
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"sub\":\""+subject+"\",\"exp\":"+exp+"}").getBytes(StandardCharsets.UTF_8));
            String sig = sha256(header + "." + payload);
            return header + "." + payload + "." + sig;
        }
        public static Optional<String> verify(String token){
            try {
                String[] parts = token.split("\\.");
                if (parts.length!=3) return Optional.empty();
                String sig = sha256(parts[0]+"."+parts[1]);
                if (!sig.equals(parts[2])) return Optional.empty();
                String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                if (!json.contains("\"exp\":")) return Optional.empty();
                long exp = Long.parseLong(json.replaceAll(".*\"exp\":(\\d+).*", "$1"));
                if (Instant.now().getEpochSecond() > exp) return Optional.empty();
                String sub = json.replaceAll(".*\"sub\":\"([^\"]+)\".*", "$1");
                return Optional.of(sub);
            } catch (Exception e){ return Optional.empty(); }
        }
        private static String sha256(String s){
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] b = md.digest(s.getBytes(StandardCharsets.UTF_8));
                return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
            } catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    public static final class Exporter {
        public static String donationsCsv(List<Donation> list){
            StringBuilder sb = new StringBuilder("id,memberId,method,amountCents,currency,createdAt,note\n");
            for (var d : list){
                sb.append(d.id()).append(',')
                        .append(d.memberId()).append(',')
                        .append(d.method()).append(',')
                        .append(d.amountCents()).append(',')
                        .append(d.currency()).append(',')
                        .append(d.createdAt()).append(',')
                        .append(escape(d.note())).append('\n');
            }
            return sb.toString();
        }
        public static String eventsIcs(List<Event> events, ZoneId zone){
            StringBuilder sb = new StringBuilder();
            sb.append("BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//ChurchApp//EN\n");
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
            for (var e : events){
                ZonedDateTime st = e.startsAt().atZone(zone).withZoneSameInstant(ZoneOffset.UTC);
                ZonedDateTime et = e.endsAt().atZone(zone).withZoneSameInstant(ZoneOffset.UTC);
                sb.append("BEGIN:VEVENT\nUID:").append(e.id()).append("@churchapp\n")
                        .append("DTSTAMP:").append(f.format(ZonedDateTime.now(ZoneOffset.UTC))).append("\n")
                        .append("DTSTART:").append(f.format(st)).append("\n")
                        .append("DTEND:").append(f.format(et)).append("\n")
                        .append("SUMMARY:").append(escapeIcs(e.title())).append("\n")
                        .append("DESCRIPTION:").append(escapeIcs(Optional.ofNullable(e.description()).orElse(""))).append("\n")
                        .append("END:VEVENT\n");
            }
            sb.append("END:VCALENDAR\n");
            return sb.toString();
        }
        private static String escape(String s){ return s==null? "" : '"' + s.replace("\"","\"\"") + '"'; }
        private static String escapeIcs(String s){ return s.replace("\n","\\n").replace(",","\\,").replace(";","\\;"); }
    }

    // ---- Notification Engine (mock) --------------------------------------------------------
    public interface Notifier {
        void send(Notification n, Member m);
    }
    public static final class MockNotifier implements Notifier {
        public void send(Notification n, Member m){
            LOG.info(() -> "[notify] " + n.channel() + " to " + m.fullName() + " (" + m.id() + "): " + n.subject());
        }
    }

    // ---- Scheduling ------------------------------------------------------------------------
    private final ScheduledExecutorService scheduler;
    private final ExecutorService pool;
    private final Clock clock;
    private final Config config;

    // ---- Repos & Services ------------------------------------------------------------------
    private final MemberRepo members = new MemberRepo();
    private final EventRepo events = new EventRepo();
    private final SermonRepo sermons = new SermonRepo();
    private final DonationRepo donations = new DonationRepo();
    private final Notifier notifier = new MockNotifier();

    // cache for computed views
    private final MiniLru<String, Object> cache;

    // queue for notifications
    private final Queue<Notification> notificationQueue = new ConcurrentLinkedQueue<>();

    // ---- Construction ----------------------------------------------------------------------
    public ChurchAppPlatform(Config cfg){
        this.config = Objects.requireNonNull(cfg);
        this.pool = Executors.newFixedThreadPool(cfg.threads());
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.clock = Clock.system(cfg.zone());
        this.cache = new MiniLru<>(cfg.cacheSize());

        // periodic tasks
        scheduler.scheduleAtFixedRate(this::dispatchNotifications, 1, cfg.notificationTick().toSeconds(), TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::logStats, 5, 10, TimeUnit.SECONDS);
    }

    // ---- API (samples you can expand) ------------------------------------------------------
    public Result<UUID> registerMember(String fullName, String email, String phone, Role role){
        List<String> errs = new ArrayList<>();
        if (fullName==null || fullName.isBlank()) errs.add("fullName required");
        if (email!=null && !email.contains("@")) errs.add("email invalid");
        if (!errs.isEmpty()) return new Err<>(errs);

        if (email!=null && members.findByEmail(email).isPresent()) {
            return new Err<>(List.of("email already registered"));
        }
        Member m = new Member(UUID.randomUUID(), fullName, email, phone, role, LocalDate.now(clock), true);
        members.save(m);
        return new Ok<>(m.id());
    }

    public Result<UUID> scheduleEvent(String title, String desc, LocalDateTime startsAt, Duration dur, Visibility vis){
        try {
            Event e = new Event(UUID.randomUUID(), title, desc, startsAt, dur, vis, Set.of());
            events.save(e);
            // enqueue reminder 1 hour before
            LocalDateTime remindAt = startsAt.minusHours(1);
            notificationQueue.add(new Push(UUID.randomUUID(), UUID.randomUUID(), "Event Reminder",
                    "“"+title+"” starts soon.", remindAt));
            cache.remove("upcoming");
            return new Ok<>(e.id());
        } catch (Exception ex){
            return new Err<>(List.of(ex.getMessage()));
        }
    }

    public Result<UUID> publishSermon(String title, String preacher, LocalDate date, String series, String passage, String mediaUrl){
        try {
            Sermon s = new Sermon(UUID.randomUUID(), title, preacher, date, series, passage, mediaUrl);
            sermons.save(s);
            cache.remove("recentSermons");
            return new Ok<>(s.id());
        } catch (Exception ex){
            return new Err<>(List.of(ex.getMessage()));
        }
    }

    public Result<UUID> recordDonation(UUID memberId, DonationMethod method, long amountCents, String currency, String note){
        if (members.find(memberId).isEmpty()) return new Err<>(List.of("member not found"));
        try {
            Donation d = new Donation(UUID.randomUUID(), memberId, method, amountCents, currency, LocalDateTime.now(clock), note);
            donations.save(d);
            cache.remove("donationTotals");
            return new Ok<>(d.id());
        } catch (Exception ex){
            return new Err<>(List.of(ex.getMessage()));
        }
    }

    public List<Event> upcomingEvents(){
        @SuppressWarnings("unchecked")
        List<Event> cached = (List<Event>) cache.get("upcoming");
        if (cached != null) return cached;
        List<Event> list = events.upcoming(clock);
        cache.put("upcoming", list);
        return list;
    }

    public List<Sermon> recentSermons(int limit){
        @SuppressWarnings("unchecked")
        List<Sermon> cached = (List<Sermon>) cache.get("recentSermons");
        if (cached != null) return cached.stream().limit(limit).toList();
        List<Sermon> list = sermons.all().stream()
                .sorted(Comparator.comparing(Sermon::date).reversed())
                .limit(Math.max(20, limit))
                .toList();
        cache.put("recentSermons", list);
        return list.stream().limit(limit).toList();
    }

    public Map<String, Long> donationTotalsByCurrency(){
        @SuppressWarnings("unchecked")
        Map<String, Long> cached = (Map<String, Long>) cache.get("donationTotals");
        if (cached != null) return cached;
        Map<String, Long> m = donations.totalsByCurrency();
        cache.put("donationTotals", m);
        return m;
    }

    public String exportDonationsCsv(){
        return Exporter.donationsCsv(donations.all().stream()
                .sorted(Comparator.comparing(Donation::createdAt).reversed())
                .toList());
    }

    public String exportEventsIcs(){
        return Exporter.eventsIcs(events.all(), config.zone());
    }

    public String issueLoginToken(UUID memberId){
        Member m = members.find(memberId).orElseThrow();
        return ToyJwt.sign(m.id().toString(), Duration.ofHours(2));
    }

    public Optional<Member> authenticate(String token){
        return ToyJwt.verify(token).flatMap(s -> {
            try { return members.find(UUID.fromString(s)); }
            catch (Exception e){ return Optional.empty(); }
        });
    }

    public String renderEventCard(UUID eventId){
        Event e = events.find(eventId).orElseThrow();
        Map<String,Object> ctx = Map.of(
                "title", e.title(),
                "time", e.startsAt().format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm")),
                "duration", e.duration().toHours() + "h",
                "desc", Optional.ofNullable(e.description()).orElse(""),
                "visibility", e.visibility());
        return Template.render("""
                <div class="card">
                  <h3>{{title}}</h3>
                  <p><strong>When:</strong> {{time}} ({{duration}})</p>
                  <p><strong>Visibility:</strong> {{visibility}}</p>
                  <p>{{desc}}</p>
                </div>
                """, ctx);
    }

    // ---- Notification Dispatcher -----------------------------------------------------------
    private void dispatchNotifications(){
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            List<Notification> due = new ArrayList<>();
            Notification n;
            while ((n = notificationQueue.peek()) != null) {
                if (n.scheduledAt().isAfter(now)) break;
                notificationQueue.poll();
                due.add(n);
            }
            for (var x : due){
                // For demo, try to find any active member; in real code, resolve by memberId or audiences.
                Member target = members.all().stream().filter(Member::active).findAny()
                        .orElse(new Member(UUID.randomUUID(), "Guest User", null, null, Role.MEMBER, LocalDate.now(), true));
                notifier.send(x, target);
            }
        } catch (Exception ex){
            LOG.log(Level.WARNING, "dispatchNotifications failed", ex);
        }
    }

    // ---- Stats ----------------------------------------------------------------------------
    private void logStats(){
        Map<String, Object> s = Map.of(
                "members", members.all().size(),
                "events", events.all().size(),
                "sermons", sermons.all().size(),
                "donations", donations.all().size(),
                "queue.notifications", notificationQueue.size(),
                "cache.size", "N/A"
        );
        LOG.info(() -> "[stats] " + s);
    }

    // ---- Closeable ------------------------------------------------------------------------
    @Override public void close() {
        scheduler.shutdownNow();
        pool.shutdownNow();
        try {
            pool.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- Demo main ------------------------------------------------------------------------
    public static void main(String[] args) throws IOException {
        Config cfg = Config.builder()
                .zone(ZoneId.of("Africa/Kampala"))
                .threads(6)
                .cacheSize(256)
                .notificationTick(Duration.ofSeconds(3))
                .build();

        try (ChurchAppPlatform app = new ChurchAppPlatform(cfg)) {

            // Register members
            var m1 = app.registerMember("Pr. Sarah N.", "sarah@example.org", "+256700000001", Role.STAFF);
            var m2 = app.registerMember("John Doe", "john@example.org", "+256700000002", Role.MEMBER);
            System.out.println("Members: " + m1 + " | " + m2);

            // Schedule events
            var ev1 = app.scheduleEvent(
                    "Sunday Service",
                    "Main worship service with communion.",
                    LocalDateTime.now(app.clock).plusMinutes(90),
                    Duration.ofHours(2),
                    Visibility.PUBLIC);
            var ev2 = app.scheduleEvent(
                    "Youth Bible Study",
                    "Study on Romans 8.",
                    LocalDateTime.now(app.clock).plusDays(1).withHour(17).withMinute(30),
                    Duration.ofHours(1).plusMinutes(30),
                    Visibility.PUBLIC);
            System.out.println("Events: " + ev1 + " | " + ev2);

            // Publish sermons
            app.publishSermon("Living by the Spirit", "Pr. Sarah N.", LocalDate.now(), "Romans Series", "Romans 8:1-17", "https://media.example/sermons/romans8.mp3");
            app.publishSermon("Faith that Works", "Elder Mark", LocalDate.now().minusWeeks(1), "James Series", "James 2:14-26", "https://media.example/sermons/james2.mp3");

            // Record donations
            UUID johnId = ((Ok<UUID>) m2).value;
            app.recordDonation(johnId, DonationMethod.MOBILE_MONEY, 500_000, "UGX", "Tithe");
            app.recordDonation(johnId, DonationMethod.CARD, 50_00, "USD", "Missions");

            // Generate a login token and authenticate
            String token = app.issueLoginToken(johnId);
            System.out.println("Token: " + token.substring(0, Math.min(30, token.length())) + "...");
            System.out.println("Auth: " + app.authenticate(token).map(Member::fullName).orElse("invalid"));

            // Get upcoming events & render a card
            var upcoming = app.upcomingEvents();
            System.out.println("Upcoming: " + upcoming.size());
            if (!upcoming.isEmpty()){
                String card = app.renderEventCard(upcoming.get(0).id());
                Files.writeString(Path.of("event-card.html"), card, StandardCharsets.UTF_8);
                System.out.println("Rendered event card -> event-card.html");
            }

            // Exports
            String csv = app.exportDonationsCsv();
            Files.writeString(Path.of("donations.csv"), csv, StandardCharsets.UTF_8);
            System.out.println("Exported donations -> donations.csv");

            String ics = app.exportEventsIcs();
            Files.writeString(Path.of("events.ics"), ics, StandardCharsets.UTF_8);
            System.out.println("Exported events calendar -> events.ics");

            // Let scheduler tick once or twice
            sleep(4000);
        }
    }

    private static void sleep(long ms){
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

