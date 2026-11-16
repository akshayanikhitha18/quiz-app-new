import com.quizsphere.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** Plain-Java checks (no test framework needed). Run with test.bat. */
public class SmokeTest {
    static void check(boolean ok, String name) { System.out.println((ok ? "PASS " : "FAIL ") + name); if (!ok) System.exit(1); }
    public static void main(String[] a) {
        PasswordHasher h = new PasswordHasher(1000);
        PasswordHasher.Hashed x = h.hashPassword("Secret123"), y = h.hashPassword("Secret123");
        check(h.verify("Secret123", x.hash(), x.salt(), x.iterations()), "hash verifies");
        check(!h.verify("wrong", x.hash(), x.salt(), x.iterations()), "wrong password fails");
        check(!x.hash().equals(y.hash()) && x.hash().length() == 64 && x.salt().length() == 32, "salted, correct lengths");

        GradingRules rules = new GradingRules(new BigDecimal("1"), new BigDecimal("0.25"), new BigDecimal("40"), 4);
        QuizGrader g = new MultipleChoiceGrader();
        List<Row> ans = new ArrayList<>();
        long[][] data = {{1, 10, 10}, {2, 20, 21}, {3, -1, 30}, {4, 40, 40}};
        for (long[] d : data) { Row r = new Row(); r.put("question_id", d[0]); r.put("selected_option_id", d[1] < 0 ? null : d[1]); r.put("correct_option_id", d[2]); ans.add(r); }
        GradeResult res = g.grade(rules, ans);
        check(res.score().compareTo(new BigDecimal("1.75")) == 0, "score 2 right -0.25 wrong = 1.75");
        check(res.correct() == 2 && res.incorrect() == 1 && res.unanswered() == 1, "counts");
        check(res.percentage().compareTo(new BigDecimal("43.75")) == 0 && res.passed(), "percentage + pass");

        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        QuizAttempt at = new QuizAttempt(1, 7, 3, "in_progress", start.plusMinutes(10), rules);
        check(at.canAcceptAnswers(start.plusMinutes(10).plusSeconds(3), 5), "within grace accepted");
        check(!at.canAcceptAnswers(start.plusMinutes(10).plusSeconds(6), 5), "after grace rejected");
        try { at.ensureOwnedBy(8); check(false, "isolation"); } catch (Errors.NotFound e) { check(true, "other student gets NotFound"); }
        QuizAttempt.SubmitValues v = at.submit(res, start.plusMinutes(15), false);
        check(v.submittedAt().equals(start.plusMinutes(10)) && v.autoSubmitted() == 1, "late submit clamped + auto flag");
        try { at.submit(res, start.plusMinutes(16), false); check(false, "dup"); } catch (Errors.Conflict e) { check("already_submitted".equals(e.code()), "second submit refused"); }

        Row sr = new Row(); sr.put("id", 1L); sr.put("username", "stu"); sr.put("email", "s@x.com"); sr.put("full_name", "Stu"); sr.put("role", "student");
        Row ar = new Row(); ar.putAll(sr); ar.put("role", "admin");
        User s = User.fromRow(sr), ad = User.fromRow(ar);
        check(s instanceof Student && ad instanceof Admin, "factory picks subclass");
        check(s.hasPermission("quiz:take") && !s.hasPermission("admin:manage"), "student permissions");
        check(ad.hasPermission("admin:manage") && !ad.hasPermission("quiz:take"), "admin permissions");
        check(!s.toPublicDict().toString().contains("hash"), "no credentials in public dict");
        try { Authorizer.authorize(s, "admin:manage"); check(false, "authz"); } catch (Errors.PermissionDenied e) { check(true, "student blocked from admin route"); }
        try { Validators.validateRegistration(new HashMap<>(Map.of("full_name", "A", "username", "x", "email", "bad", "password", "short", "confirm_password", "x"))); check(false, "val"); }
        catch (Errors.ValidationError e) { check(e.fields().size() >= 4, "registration validation rejects bad input"); }
    }
}
