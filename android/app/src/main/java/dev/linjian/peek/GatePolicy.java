package dev.linjian.peek;

/** Pure decisions shared by the Android implementation and host regression tests. */
final class GatePolicy {
    private GatePolicy() { }

    static boolean isConfirmedTransition(String previous, String next) {
        return !next.isEmpty() && !next.equals(previous);
    }

    static String resolve(String root, String active, String focused, boolean ownOverlayActive) {
        // An accessibility overlay can be touched while the app underneath retains input focus.
        if (ownOverlayActive) return focused;
        String candidate = active.isEmpty() ? root : active;
        // Conflicting snapshots during a task animation are not proof of a foreground app.
        if (!candidate.isEmpty() && !focused.isEmpty() && !candidate.equals(focused)) return "";
        return candidate.isEmpty() ? focused : candidate;
    }

    static boolean mayFallback(long attempt, long currentAttempt, String target,
            String active, boolean locked, boolean gateCurrentlyConfirmed, boolean overlay) {
        return attempt == currentAttempt && !target.isEmpty() && target.equals(active)
                && locked && !gateCurrentlyConfirmed && !overlay;
    }

    static boolean needsAttempt(String target, String pendingTarget, boolean transition,
            boolean pending, boolean overlay) {
        return !overlay && (transition || !pending || !target.equals(pendingTarget));
    }
}
