# Phase 1 — APK Manifest Analysis

**APK:** `/home/lk1/craft_apps/simple_notes/touchnotes-7-0-4.apk` (symlink `~/touchnotes-7-0-4.apk`)
**Tooling:** `aapt2 dump badging/xmltree` + `apktool 2.12.1` decode (shell, static analysis). No Craft Anything evidence ids available (clean-room; no target opened) — search boundary stated per claim where needed.

## Package identity
- Package name (original): `com.newskyer.draw`  (evidence: shell:aapt2 badging + apktool decode of AndroidManifest.xml)
- App label: `touchnotes` (application-label)
- Version name: `7.0.4`; version code: `142512`
- compileSdkVersion: `36` (Android 16); platformBuildVersionCode `36`
- sharedUserId: `com.newskyer.draw` (sharedUserMaxSdkVersion 32) — note: shared user id; clone must NOT reuse it.
- Application class: `com.ctZtamt.cym.ilajjtl.MyApplication` (obfuscated entry; likely a wrapper/protection layer)

## SDK levels
- minSdkVersion: `24` (Android 7.0 Nougat)
- targetSdkVersion: `34` (Android 14)

## Main activity / launcher
- Launcher: `com.newskyer.draw.activity.SplashActivity` (exported, MAIN/LAUNCHER, theme `Starting`)
- Real main screen: `com.newskyer.draw.PadActivity` reached via activity-alias `com.newskyer.draw.MainActivity` (targetActivity = PadActivity; exported; MAIN action + newskyer.intent.action.DRAW)
- Exported: true for both.

## Exported components (notable, app-owned)
Activities:
- `com.newskyer.draw.activity.SplashActivity`
- `com.newskyer.draw.PadActivity` (via MainActivity alias)
- `com.newskyer.draw.activity.PayProActivity`
- `com.newskyer.draw.activity.LoginHuaweiDriveActivity`, `com.newskyer.draw.baidusync.BaiduLoginActivity`
- `com.newskyer.draw.file.activity.BookshelfActivity`, `MobileFileSelectActivity`, `DirectionSelectorActivity`, `SettingsActivity`
- `com.newskyer.draw.activity.CaptureSeviceActivity`, `com.newskyer.draw.activity.TNNewsActivity`
- `com.newskyer.draw.activity.openad.SplashAdActivity`
- `com.newskyer.draw.wxapi.WXEntryActivity`, `WXPayEntryActivity`
- `com.newskyer.draw.doc_scan.DocScanActivity`
- `com.newskyer.draw.flashCards.FlashCardActivity`
- `com.newskyer.meetingpad.fileselector.activity.FileSelectActivity`, `PathSelectorActivity`
- `com.cicoe.user.mobile.activity.UserInfoSettingActivity`, `MobileLoginActivity`, `MeetingDetailsActivity`

Services (exported, app-owned):
- `com.newskyer.draw.service.AiService`, `UploadNotesService`, `CaptureScreenService`, `CaptureService`, `OcrScreenService`, `ColorSnapService`, `NetWorkCallbackService`
- `com.newskyer.draw.service.floatBall.eyecase.*` (FloatCaptureService, EyeCareService, OcrResultService, NightService, NotificationCountDownService)
- `com.newskyer.draw.widget.ViewsService`
- `net.cicoe.audio.ASRService`

Receivers (exported, app-owned):
- `com.newskyer.draw.file.reciever.NotificationReceiver`, `CountDownNotificationReceiver`
- `com.newskyer.draw.floatBall.utils.NotificationReceiver`
- `com.newskyer.draw.widget.AppWidget`

Providers (exported, app-owned): PushProvider (Huawei), plus many third-party SDK providers.

(Third-party SDK components — Firebase, Google Play, Huawei HMS, Bytedance/Pangle, Kwai, Baidu, Dropbox, SensorsData, etc. — also present; clone will not replicate ad/analytics SDKs.)

## Permissions (56 declared)
Full list from manifest (shell:aapt2). Notable for offline use:
- Storage: `READ/WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `MOUNT_UNMOUNT_FILESYSTEMS`, `WRITE_MEDIA_STORAGE`, `ACCESS_ALL_EXTERNAL_STORAGE` — heavy storage access; clone should use scoped storage / SAF instead.
- Camera/mic: `CAMERA`, `RECORD_AUDIO`, `FLASHLIGHT`
- Location: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`
- Network: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_NETWORK_STATE`, `CHANGE_WIFI_MULTICAST_STATE`
- Phone/identity: `READ_PHONE_STATE`, `GET_TASKS`, `GET_INSTALLED_APPS`, `QUERY_ALL_PACKAGES`
- System/alerts: `SYSTEM_ALERT_WINDOW`, `SYSTEM_OVERLAY_WINDOW`, `EXPAND_STATUS_BAR`, `REORDER_TASKS`, `USE_FULL_SCREEN_INTENT`, `SET_ALARM`, `VIBRATE`, `WAKE_LOCK`
- Media projection: `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `PROJECT_MEDIA`
- Foreground service: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`
- Notifications: `POST_NOTIFICATIONS`
- Install packages: `REQUEST_INSTALL_PACKAGES`
- Billing: `com.android.vending.BILLING`
- Ads/attribution: `com.google.android.gms.permission.AD_ID`, vendor-specific ad switches
- Misc vendor: `com.oplus.ipemanager.permission.receiver.DOUBLE_CLICK`, `com.oplus.permission.safe.IOT`, `com.miui.systemAdSolution.adSwitch.PROVIDER`, `com.hihonor...`, `com.asus.msa.SupplementaryDID.ACCESS`, `com.heytap...`, Huawei app market, etc.

## Hardware features
Declared `<uses-feature>`:
- Required: `android.hardware.camera`, `android.hardware.camera.autofocus`
- Optional (required=false): audio.output, audio.pro, microphone, camera.any, location.gps, location.network, sensor.accelerometer, wifi, sip.voip
Implied by aapt2: faketouch, location, screen.landscape, screen.portrait.

## App attributes relevant to cloning
- `allowBackup=false`
- `largeHeap=true`
- `requestLegacyExternalStorage=true`
- `usesCleartextTraffic=true`
- `usesNonSdkApi=true`
- `extractNativeLibs=true`
- `hardwareAccelerated=true`
- `supportsRtl=true`

## Offline-use implications
- App declares `INTERNET` and many network/ad/analytics SDKs (Firebase, Google Play, Huawei HMS, Pangle/Bytedance, Kwai, Baidu, Dropbox, SensorsData, Umeng). Clone is offline-only: drop these.
- Storage permissions are broad; clone should use app-private + SAF.
- Media projection / capture services are part of "screen capture → note" feature; clone may defer (out of scope per SCOPE.md unless listed).
- minSdk 24 → clone target minSdk 21 per project decision (Rule #9/STATE.md) is LOWER than original; ensure no API 24+ usage without compat, or raise to 24. Flag for architect.

## Notes / unknowns
- Exact obfuscated mapping (ProGuard/R8) not recovered; component names above from manifest only (shell:aapt2 + apktool). Internal class structure unknown at this stage (Phase 2).
- Whether `com.ctZtamt.cym.ilajjtl.MyApplication` is a packer/protector wrapper is unknown (search boundary: static manifest only).
