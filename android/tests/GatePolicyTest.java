package dev.linjian.peek;

/** Host-side regression checks; these do not claim to simulate HyperOS windows. */
public final class GatePolicyTest {
    private static int checks;
    private static final String XHS = "com.xingin.xhs", SELF = "dev.linjian.peek";
    private static final String RECENTS = "com.android.systemui", HOME = "com.miui.home";
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(XHS.equals(GatePolicy.resolve(XHS, XHS, XHS, false)), "desktop -> target");
        check(RECENTS.equals(GatePolicy.resolve(XHS, RECENTS, RECENTS, false)), "prefer current windows over stale root");
        check(GatePolicy.resolve(XHS, XHS, RECENTS, false).isEmpty(), "animation conflict must not gate/home");
        check(GatePolicy.resolve("", "", "", false).isEmpty(), "unknown must remain unknown");
        check(XHS.equals(GatePolicy.resolve("", "", XHS, false)), "focused window fallback");
        check(XHS.equals(GatePolicy.resolve(SELF, "", XHS, true)), "touching own overlay still tracks target");
        check(HOME.equals(GatePolicy.resolve(SELF, "", HOME, true)), "overlay cannot hide launcher transition");
        check(RECENTS.equals(GatePolicy.resolve(SELF, "", RECENTS, true)), "overlay cannot hide recents transition");
        check(GatePolicy.resolve(SELF, "", "", true).isEmpty(), "overlay without underlying proof is removed");

        // Five reentries at both fast and slow speeds: transition wins over pending state.
        for (int delay : new int[] {20, 100, 349, 700, 5000}) {
            for (int visit = 0; visit < 5; visit++) {
                check(GatePolicy.needsAttempt(XHS, XHS, true, true, false), "reentry lost at " + delay + " visit " + visit);
                check(!GatePolicy.needsAttempt(XHS, XHS, false, true, false), "duplicate content event restarts gate");
            }
        }
        check(GatePolicy.needsAttempt(XHS, "", false, false, false), "command locks already foreground app");
        check(!GatePolicy.needsAttempt(XHS, XHS, false, false, true), "overlay coverage starts another gate");
        check(GatePolicy.mayFallback(7, 7, XHS, XHS, true, false, false), "resume then pause must not suppress fallback");
        check(!GatePolicy.mayFallback(7, 8, XHS, XHS, true, false, false), "stale runnable must not act on a new visit");
        check(!GatePolicy.mayFallback(7, 7, XHS, XHS, false, false, false), "expiry/unlock cancels fallback");
        check(!GatePolicy.mayFallback(7, 7, XHS, XHS, true, false, true), "existing overlay cancels home");
        for (String safe : new String[] {SELF, RECENTS, HOME, "com.openai.chatgpt", ""}) {
            for (boolean resumedBefore : new boolean[] {false, true}) {
                check(!GatePolicy.mayFallback(7, 7, XHS, safe, true, resumedBefore, false), "safe foreground must not fallback: " + safe);
            }
        }
        System.out.println("GatePolicy: " + checks + " checks passed (host decisions, not device tests)");
    }
}
