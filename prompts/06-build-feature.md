# Phase 6: Build Feature — {{FEATURE_NAME}}

**Agent:** builder
**Rule:** One feature per task. Read spec in `state/FEATURES.md`.

Tasks:
1. Implement feature in `app/src/main/java/personal/notes/touch/`.
2. Build (`./gradlew assembleDebug`).
3. Install clone (`adb install -r ...`).
4. Launch, screenshot, save to `reference/screens/test-{{FEATURE_NAME}}.png`.
5. Capture log (`adb logcat -d > logs/{{FEATURE_NAME}}-test.log`).
6. Update `state/FEATURES.md` and `state/STATE.md`.
