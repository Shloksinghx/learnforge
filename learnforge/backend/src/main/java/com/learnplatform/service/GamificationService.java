package com.learnplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnplatform.domain.*;
import com.learnplatform.repo.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** XP, levels, badges, streaks, daily challenges and the leaderboard. */
@Service
public class GamificationService {
    public static final int[] LEVEL_XP = {0, 100, 300, 700, 1300, 2200};
    public static final String[] LEVEL_NAMES = {"Novice", "Learner", "Practitioner", "Specialist", "Expert", "Master"};
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final Map<String, String[]> BADGES = new LinkedHashMap<>();
    static {
        BADGES.put("FIRST_STEPS", new String[] {"First Steps", "Complete your first lesson"});
        BADGES.put("PRODUCT_EXPERT", new String[] {"Product Expert", "Complete every lesson in a course"});
        BADGES.put("KNOWLEDGE_MASTER", new String[] {"Knowledge Master", "Score 90% or more on a test"});
        BADGES.put("SHARPSHOOTER", new String[] {"Sharpshooter", "Score 100% on a test"});
        BADGES.put("OBJECTION_HANDLER", new String[] {"Objection Handler", "Score 80% or more on objection handling in a simulation"});
        BADGES.put("COMMUNICATION_PRO", new String[] {"Communication Pro", "Score 80% or more on communication in a simulation"});
        BADGES.put("SIM_PRO", new String[] {"Simulation Pro", "Finish 3 simulations"});
        BADGES.put("ON_A_ROLL", new String[] {"On a Roll", "Learn 3 days in a row"});
        BADGES.put("WEEK_WARRIOR", new String[] {"Week Warrior", "Learn 7 days in a row"});
    }

    public record BadgeView(String code, String name, String description, boolean earned) {}
    public record ChallengeView(String title, String description, int progress, int target, boolean done) {}
    public record Profile(int xp, int level, String levelName, int levelStartXp, Integer nextLevelXp, int streak,
                          List<BadgeView> badges, List<ChallengeView> challenges, String todayChallenge) {}
    public record LeaderRow(int rank, UUID userId, String name, int xp, String levelName, boolean me) {}

    private final XpEventRepository xp;
    private final UserBadgeRepository badges;
    private final AttemptRepository attempts;
    private final SimulationRepository simulations;
    private final EnrolmentRepository enrolments;
    private final LessonRepository lessons;
    private final LessonCompletionRepository completions;
    private final AppUserRepository users;
    private final CompetencyService competencies;

    public GamificationService(XpEventRepository xp, UserBadgeRepository badges, AttemptRepository attempts,
                               SimulationRepository simulations, EnrolmentRepository enrolments,
                               LessonRepository lessons, LessonCompletionRepository completions,
                               AppUserRepository users, CompetencyService competencies) {
        this.xp = xp;
        this.badges = badges;
        this.attempts = attempts;
        this.simulations = simulations;
        this.enrolments = enrolments;
        this.lessons = lessons;
        this.completions = completions;
        this.users = users;
        this.competencies = competencies;
    }

    /** Idempotent: the same (user, type, ref) is only ever rewarded once. */
    public void award(UUID orgId, UUID userId, String type, String refId, int points) {
        if (xp.existsByUserIdAndTypeAndRefId(userId, type, refId)) return;
        XpEvent e = new XpEvent();
        e.setOrgId(orgId);
        e.setUserId(userId);
        e.setType(type);
        e.setRefId(refId);
        e.setPoints(points);
        try {
            xp.save(e);
        } catch (DataIntegrityViolationException ignored) {
            // awarded concurrently
        }
    }

    public void refresh(UUID orgId, UUID userId) {
        Set<String> have = badges.findByUserId(userId).stream().map(UserBadge::getCode).collect(Collectors.toSet());
        for (String code : evaluate(userId)) {
            if (have.contains(code)) continue;
            UserBadge b = new UserBadge();
            b.setOrgId(orgId);
            b.setUserId(userId);
            b.setCode(code);
            try {
                badges.save(b);
            } catch (DataIntegrityViolationException ignored) {
                // earned concurrently
            }
        }
    }

    public Profile profile(UUID userId) {
        List<XpEvent> events = xp.findByUserIdOrderByCreatedAtDesc(userId);
        int total = events.stream().mapToInt(XpEvent::getPoints).sum();
        int level = levelIndex(total);
        Set<String> earned = badges.findByUserId(userId).stream().map(UserBadge::getCode).collect(Collectors.toSet());
        List<BadgeView> bv = new ArrayList<>();
        for (Map.Entry<String, String[]> b : BADGES.entrySet()) {
            bv.add(new BadgeView(b.getKey(), b.getValue()[0], b.getValue()[1], earned.contains(b.getKey())));
        }
        List<ChallengeView> ch = challenges(userId, streak(events));
        List<ChallengeView> open = ch.stream().filter(c -> !c.done()).toList();
        String today = open.isEmpty() ? null : open.get(LocalDate.now(IST).getDayOfYear() % open.size()).title();
        return new Profile(total, level + 1, LEVEL_NAMES[level], LEVEL_XP[level],
                level + 1 < LEVEL_XP.length ? LEVEL_XP[level + 1] : null, streak(events), bv, ch, today);
    }

    public List<LeaderRow> leaderboard(UUID orgId, UUID meId, int limit) {
        Map<UUID, Integer> totals = new HashMap<>();
        for (XpEvent e : xp.findByOrgId(orgId)) totals.merge(e.getUserId(), e.getPoints(), Integer::sum);
        List<Map.Entry<UUID, Integer>> sorted = totals.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).limit(limit).toList();
        Map<UUID, AppUser> byId = users.findAllById(sorted.stream().map(Map.Entry::getKey).toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, u -> u));
        List<LeaderRow> out = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<UUID, Integer> e : sorted) {
            AppUser u = byId.get(e.getKey());
            if (u == null) continue;
            out.add(new LeaderRow(rank++, u.getId(), u.getFullName(), e.getValue(),
                    LEVEL_NAMES[levelIndex(e.getValue())], u.getId().equals(meId)));
        }
        return out;
    }

    // ---- internals ----
    private static int levelIndex(int xpTotal) {
        int idx = 0;
        for (int i = 0; i < LEVEL_XP.length; i++) if (xpTotal >= LEVEL_XP[i]) idx = i;
        return idx;
    }

    private static int streak(List<XpEvent> events) {
        Set<LocalDate> days = events.stream().map(e -> e.getCreatedAt().atZone(IST).toLocalDate())
                .collect(Collectors.toSet());
        LocalDate d = LocalDate.now(IST);
        if (!days.contains(d)) d = d.minusDays(1);
        int n = 0;
        while (days.contains(d)) {
            n++;
            d = d.minusDays(1);
        }
        return n;
    }

    private List<Attempt> submitted(UUID userId) {
        return attempts.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(a -> "SUBMITTED".equals(a.getStatus()) && a.getScore() != null).toList();
    }

    private List<Simulation> finishedSims(UUID userId) {
        return simulations.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(s -> "FINISHED".equals(s.getStatus())).toList();
    }

    private Set<String> evaluate(UUID userId) {
        Set<String> out = new HashSet<>();
        List<XpEvent> events = xp.findByUserIdOrderByCreatedAtDesc(userId);
        if (events.stream().anyMatch(e -> "LESSON".equals(e.getType()))) out.add("FIRST_STEPS");
        int streak = streak(events);
        if (streak >= 3) out.add("ON_A_ROLL");
        if (streak >= 7) out.add("WEEK_WARRIOR");

        List<Attempt> done = submitted(userId);
        if (done.stream().anyMatch(a -> a.getScore() >= 90)) out.add("KNOWLEDGE_MASTER");
        if (done.stream().anyMatch(a -> a.getScore() >= 100)) out.add("SHARPSHOOTER");

        List<Simulation> sims = finishedSims(userId);
        if (sims.size() >= 3) out.add("SIM_PRO");
        for (Simulation s : sims) {
            JsonNode sc = competencies.scores(s);
            if (sc.path("objectionHandling").asInt(0) >= 80) out.add("OBJECTION_HANDLER");
            if (sc.path("communication").asInt(0) >= 80) out.add("COMMUNICATION_PRO");
        }

        for (Enrolment e : enrolments.findByUserId(userId)) {
            List<Lesson> ls = lessons.findByCourseIdOrderBySortOrderAsc(e.getCourseId());
            if (ls.isEmpty()) continue;
            int n = completions.findByUserIdAndLessonIdIn(userId, ls.stream().map(Lesson::getId).toList()).size();
            if (n >= ls.size()) {
                out.add("PRODUCT_EXPERT");
                break;
            }
        }
        return out;
    }

    private List<ChallengeView> challenges(UUID userId, int streak) {
        List<Attempt> done = submitted(userId);
        int sims = Math.min(3, finishedSims(userId).size());
        int passed = (int) Math.min(3, done.stream().filter(a -> a.getScore() >= 60).count());
        boolean perfect = done.stream().anyMatch(a -> a.getScore() >= 100);

        boolean improved = false;
        Map<UUID, Integer> best = new HashMap<>();
        List<Attempt> chronological = new ArrayList<>(done);
        Collections.reverse(chronological);
        for (Attempt a : chronological) {
            Integer prev = best.get(a.getCourseId());
            if (prev != null && a.getScore() > prev) improved = true;
            if (prev == null || a.getScore() > prev) best.put(a.getCourseId(), a.getScore());
        }

        List<ChallengeView> out = new ArrayList<>();
        out.add(new ChallengeView("Complete 3 simulations", "Finish three AI role-plays", sims, 3, sims >= 3));
        out.add(new ChallengeView("7-Day Learning Challenge", "Learn every day for a week", Math.min(7, streak), 7, streak >= 7));
        out.add(new ChallengeView("Beat Your Previous Score", "Score higher than your last try on a course test", improved ? 1 : 0, 1, improved));
        out.add(new ChallengeView("100% Accuracy Challenge", "Get every question right in a test", perfect ? 1 : 0, 1, perfect));
        out.add(new ChallengeView("Pass 3 tests", "Score 60% or more on three tests", passed, 3, passed >= 3));
        return out;
    }
}
