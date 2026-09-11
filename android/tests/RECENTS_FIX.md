# App Gate / HyperOS Recents repair

Base: fork `Dandelion2007/linjian-peek-public`, main commit
`1c7d57cac1026273f8b2bebf72613dd7e0914751` (not upstream main).

## Findings verified in this base

- ScreenshotService forwarded the source package of four accessibility event types
  directly into AppGate. Events without a package were ignored by the gate.
- `confirmedGateAttempt` survived pause/destroy, and `>=` allowed a historical
  resume to cancel later 700/1400 ms fallback checks without consulting windows.
- onNewIntent marked the Activity visible even when it was not resumed.
- The 350 ms package/time guard did not distinguish a new foreground visit.
- Overlay had no target-package lifetime or lock-expiry callback. Its focusable
  application window could also obscure the foreground evidence needed for cleanup.
- The command-time check read the same event-based `currentPackage()`.
- LockActivity refresh finishes when its lock expires; the source does not justify
  treating an arbitrary delayed finish as the root cause of the recorded behavior.

These are verified code defects. The precise ordering on this user's HyperOS phone
still needs the new runtime trace; no emulator or host test proves that ordering.

## Changes and boundaries

ScreenshotService now resolves fresh active-root and active/focused window packages.
Conflicting or missing window evidence produces unknown, never an event-package
fallback. Own overlay windows are identified by window ID, not by package alone.
Events trigger checks plus bounded 100/450 ms retries for window transitions or
unknown state; retries do not reschedule themselves. Null-package events also work.
Gate checks run before the existing screen-tree traversal.

AppGate invalidates pending callbacks on confirmed foreground changes. Unknown
windows suspend coverage without pretending another app was entered; when evidence
returns the same attempt is reverified without launching a duplicate Activity. Only the current
attempt on the currently locked foreground target can overlay or fall back Home.
Lifecycle visibility is owned by the Activity instance, revoked on pause, and only
confirmed when the resolved foreground is this app. Pause requests a new window
check plus bounded retries. Historical confirmation cannot suppress another visit.
Repeated content events reuse the pending attempt or target overlay. A new visit
is not timestamp-debounced. Commands recheck window state on the main thread.

The fallback uses TYPE_ACCESSIBILITY_OVERLAY through ScreenshotService's window
manager when overlay permission is granted. Android documents that this window
type keeps covered windows introspectable. FLAG_NOT_FOCUSABLE preserves underlying
input focus while the full-screen clickable view consumes touches. The overlay is
removed on safe/unknown foreground, unlock, expiry, or service disconnect; extension
of the lock reschedules its expiry. A details-button launch gets its own verified
fallback sequence. Launcher/SystemUI are protected from automatic gate actions.

Reference: https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo#TYPE_ACCESSIBILITY_OVERLAY

DebugState keeps a separate bounded 48,000-character trace, batched to preferences,
also written to Logcat tag AppGate and shown in the existing debug view. It includes
event source/type, active/root/focused package, window ID/type/layer, attempt,
visible/confirmed state, overlay target, lifecycle/task ID, and action/skip reason.
It records package/window metadata, not screen text or credentials.

The manifest already opts into window retrieval through accessibility_config.xml.
Both XML files and all unrelated UI, server/MCP files are unchanged. Other callers
of ScreenshotService.currentPackage(), including FocusMode, keep their old semantics.
Existing NEW_TASK/CLEAR_TOP/SINGLE_TOP launch flags are retained.

## Build and upgrade identity

Run `bash android/build.sh`; host policy checks run before the Android build.
Output: `android/Zhangxinchuang-public-v0.3.8.6-recents-fix.apk`.
Package `dev.linjian.peek`, versionCode `30806`, versionName `0.3.8.6` are retained.
The fixed public signing key is unchanged. Expected certificate SHA-256:
`aea75c9b2b5f5c42d56b72d4a69a79a38e1c57f27db021017be8656bc8f002fb`.

The baseline Actions artifact for commit 1c7d57c was downloaded and inspected:
APK SHA-256 `bfd521b18f327f82a41f252f774f0fd2b738d7dcd0f6138ae255d3db78fe7e09`.
Its package/version/certificate match the fixed build. This is the fork's baseline
artifact, not a byte-for-byte claim about a separately named ZIP on the phone.
Install as an update; uninstalling is unnecessary.

## Validation boundary and device acceptance

Host GatePolicy tests check foreground resolution, safe/unknown fallback rejection,
attempt identity, expiry, overlay deduplication, and repeated transition decisions.
These are decision tests, not execution of real Android lifecycle/task animations.
The APK build verifies Java/resources/DEX, alignment/signature, package/version, and
prints the APK SHA-256. The existing push-triggered GitHub workflow uploads the APK
as `zhangxinchuang-public-debug-apk`.

All eight phone acceptance scenarios remain to be tested after installing:

| Scenario | Expected |
| --- | --- |
| Desktop -> locked XHS | Gate or target overlay intercepts |
| Gate -> Recents -> XHS, five times | Every reentry intercepts |
| Fast reentry and reentry after several seconds | Same result, no time debounce bypass |
| Gate -> Recents -> ChatGPT/desktop | No forced Gate or fallback Home |
| Gate remains in foreground past 700/1400 ms | No fallback Home |
| Overlay -> Recents/desktop/other app | Overlay removed |
| Lock expires | XHS opens normally |
| Lock command while XHS is already foreground | Window recheck intercepts |

If a scenario fails, capture the AppGate trace immediately along with the screen
recording. Resolve package/window evidence before making another lifecycle guess.
