# Freemote — Agent Memory File

## Project Overview

Freemote is an Android app that turns your phone into a universal TV remote. Primary target: **Samsung Tizen TVs** (WebSocket protocol). Also has partial/skeleton support for ~13 other TV brands. Built in Java, targeting API 26-34.

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Output
app/build/outputs/apk/debug/Freemote-OC-debug-<timestamp>.apk

# Install to test phone
adb connect 100.69.196.80:5555
adb -s 100.69.196.80:5555 install -r <apk-path>
```

## Key Architecture

### Connection layers
```
RemoteActivity (UI) → RemoteService (background) → RemoteController (interface)
    → SamsungRemote / AndroidTvRemote / LgRemote / etc. → WebSocket / ADB / REST
```

### Navigation modes
- `NAV_DPAD = 0` — D-pad arrows + OK button
- `NAV_TOUCH = 1` — Absolute-position touchpad (finger → TV coordinates)
- `NAV_MEDIA = 2` — Media transport controls (play, prev, next, RW, FF, vol)

### Side columns (swappable via FrameLayout)
| Mode | Left | Right |
|---|---|---|
| D-pad | Vol+/Vol−/Mute | Ch+/Ch−/ChEntry |
| Touchpad | R/M/L mouse buttons | Scroll indicator (visual only) |
| Media | Hidden | Hidden |

## Wiring Rules

- **Hold-to-repeat buttons** use `setRepeatListener(btn, keyCode)` — 400ms initial, 150ms repeat
- Currently wired with repeat: Vol+/−, Ch+/−, D-pad arrows, RW/FF, Prev/Next
- Not wired with repeat: OK, Mute, ChEntry, Prev as skip, PlayPause (toggle — doesn't make sense)
- All icon drawables use `?attr/colorButtonIcon` (no hardcoded colors) unless brand-specific (`tint="@null"`)
- Side column buttons are 48dp squares with `bg_button` background
- Media transport buttons use emoji text (⏮/⏭) with `app:backgroundTint="@null"`

## Key Protocol Details (Samsung Tizen)

- **Port 8002** WSS (secure WebSocket, tried first) → **8001** WS (fallback) → REST API (app launch only)
- Keys sent as JSON: `{"method":"ms.remote.control","params":{"Cmd":"Click","DataOfCmd":"KEY_VOLUP","Option":"false","TypeOfRemote":"SendRemoteKey"}}`
- Mouse: `ProcessMouseDevice` with absolute coordinates (x, y), needs `sendMouseActivate()` first
- Text: `SendInputString` with base64-encoded payload
- Volume keys auto-normalize: `KEY_VOLUMEUP` → `KEY_VOLUP`, `KEY_VOLUMEDOWN` → `KEY_VOLDOWN`
- App launch: HTTP POST to `http://<ip>:8001/api/v2/applications/<appId>`
- TV info: HTTP GET `http://<ip>:8001/api/v2/` returns model, MAC, resolution
- Auto-reconnect: up to 5 attempts with 1-10s backoff; falls through LegacyWS → REST

## Settings State

| Feature | UI | Implementation |
|---|---|---|
| Theme picker | ✅ Yes | Dropdown + Save button, 1 custom theme |
| Device Management | ✅ Yes | Opens `DeviceManagementActivity` |
| About dialog | ✅ Yes | Opens `AboutDialogFragment` |
| Touchpad sensitivity | ❌ No UI | `touchpad_sensitivity` pref key exists, wired in `setupTouchpad()` |
| Shortcut button assignment | ❌ No UI | `slot_app_` / `slot_icon_` pref keys exist, wired in `loadShortcuts()` |
| Bluetooth Remote | ❌ Removed | Was a dead "Coming soon" row — no Bluetooth HID implementation |

## Bluetooth HID Feasibility

Android's `BluetoothHidDevice` API (API 29+) can make the phone act as a HID keyboard/mouse. Samsung Tizen TVs **won't accept** standard HID connections (they use proprietary BLE remote pairing), but Android TV, LG webOS, and Roku likely would. Estimate ~500-700 lines of new code for a complete implementation + wiring.

## Current Gaps (noted during session)

1. Touchpad sensitivity slider — pref key exists, code reads it, but no settings UI to control it
2. Shortcut button assignment UI — same pattern, no settings screen for it
3. Media panel Prev/Next/PlayPause don't have hold-to-repeat (intentional — toggle/step behavior)
4. Mouse wheel simulated via Y-moves on Samsung (no native scroll command in protocol)

## Touchpad Implementation

Two modes exist:
- **Absolute** (`layoutTouchpadAlt`, active): Finger position maps to TV screen coordinates. Short tap (<300ms) = left click, long tap = right click.
- **Relative** (`layoutTouchpad`, disabled): Laptop-style relative swipe with sensitivity adjustment. Currently `isTouchpadMode = false`.

## Voice Commands

`VoiceCommandActivity` (translucent overlay) listens via speech recognition, then broadcasts recognized text via `LocalBroadcastManager` → `RemoteActivity` receives and sends to TV as typed text.

## Screen Casting

Uses `MediaProjection` API (screen capture permission) → `ScreenCastingService`. Cast dialog offers app launch options (Prime Video, YouTube, Disney+) or full screen mirror.
