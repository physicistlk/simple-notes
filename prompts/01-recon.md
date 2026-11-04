# Phase 1: Reconnaissance

**Agent:** analyst
**Goal:** Extract basic device and APK information without a physical tablet connection.

## Context from STATE.md
- APK Path: {{APK_PATH}}
- Tablet Serial: {{SERIAL}}
- Project Root: {{PROJECT_ROOT}}

## Your Tasks

1. **APK Manifest Analysis:**
   - Use Craft Anything to extract the AndroidManifest.xml from the APK
   - Extract: package name, minSdkVersion, targetSdkVersion, permissions
   - Identify the main activity and any exported components
   - Write findings to `analysis/01-manifest.md`

2. **APK Structure:**
   - List the APK's directory structure (classes.dex, resources.arsc, lib/, res/)
   - Identify the number of DEX files and any native libraries
   - Write to `analysis/01-structure.md`

3. **Device Requirements:**
   - Based on the manifest, document the minimum Android version required
   - List any hardware features required (camera, sensors, etc.)
   - Note any permissions that might affect offline use

4. **Update DEVICE.md:**
   Create or update `state/DEVICE.md` with the fields from the blackboard.

## Success Criteria
- `state/DEVICE.md` exists with all fields populated (serial can be PENDING)
- `analysis/01-manifest.md` contains the extracted manifest data
- `analysis/01-structure.md` documents the APK layout
- You have identified the original app's package name
