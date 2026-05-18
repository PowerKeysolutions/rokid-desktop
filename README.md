# rokid-desktop

Stream the desktop of a Linux PC to **Rokid AR glasses** in real time via WebRTC, with camera feedback from the glasses back to the PC for gesture control.

![Platform](https://img.shields.io/badge/platform-Linux%20%7C%20Android-blue)
![Android API](https://img.shields.io/badge/Android%20API-32%20(Android%2012)-green)
![Java](https://img.shields.io/badge/Java-11+-orange)
![License](https://img.shields.io/badge/license-MIT-lightgrey)
![mediamtx](https://img.shields.io/badge/mediamtx-v1.18+-purple)

> Tested on: Rokid Max / Air 2 Pro — YodaOS (Android 12, SDK 32) — NUC running Ubuntu/GNOME

---

## Table of Contents

- [How it works](#how-it-works)
- [Requirements](#requirements)
- [Quick Start](#quick-start)
- [Headless / Virtual Display Setup](#headless--virtual-display-setup)
- [Network Requirements](#network-requirements)
- [Project Structure](#project-structure)
- [Build the APK](#build-the-apk)
- [Auto-start on Boot](#auto-start-on-boot-linux-systemd)
- [Troubleshooting](#troubleshooting)
- [Known Limitations](#known-limitations)
- [Roadmap](#roadmap)
- [Related Projects](#related-projects)
- [Contributing](#contributing)
- [License](#license)

---

## How it works

Bidirectional system: the NUC streams its desktop to the glasses, and the glasses stream their camera back to the NUC for gesture recognition.

```
Linux PC (NUC)                                Rokid Glasses (Android 12)
──────────────────────────────────────────    ──────────────────────────────
Xvfb :1 → ffmpeg x11grab → RTSP :8554    →   mediamtx WHEP :8889
mediamtx → WebRTC + HLS                   →   APK WebView (fullscreen)

                                           ←   CameraService → TCP :8082
NUC receives JPEG frames (gesture input)  ←   Camera2 640×480 @ ~30fps
```

The APK opens a fullscreen **WebView** that loads mediamtx's built-in WebRTC player. No native WebRTC SDK needed.

The Rokid display is **additive** (dark pixels = transparent). The desktop background is set to `grey20` — dark enough to be nearly invisible as an AR overlay.

---

## Requirements

### Linux PC (server)
- `ffmpeg` with x11grab support (`apt install ffmpeg`)
- `mediamtx` v1.18+ — [releases](https://github.com/bluenviron/mediamtx/releases)
- `Xvfb` for the virtual display (`apt install xvfb`)
- `xsetroot` (`apt install x11-utils`)
- systemd service `rokid-session` managing the virtual display (see [Headless setup](#headless--virtual-display-setup))
- A TCP listener on `:8082` to receive camera frames from the glasses

### Rokid Glasses
- USB debugging enabled (Settings → Developer options)
- `adb` installed on the control PC
- WiFi on same LAN as the Linux PC
- Camera permission granted to the app

### Build dependencies (APK only)
- Android Studio or JDK 21 + Android SDK
- Gradle wrapper included (`gradlew.bat` on Windows)

---

## Quick Start

### 1. Linux PC — start the stream

```bash
cd server/
./start.sh
```

Stream will be available at:
- **WebRTC (WHEP)**: `http://<NUC-IP>:8889/desktop/`
- **HLS**: `http://<NUC-IP>:8888/desktop/index.m3u8`

The script uses `DISPLAY :1` (Xvfb). See [Headless setup](#headless--virtual-display-setup) for details.

### 2. Install APK on glasses

```bash
adb install -r apk/app/build/outputs/apk/debug/app-debug.apk
```

### 3. Launch from glasses

Open **Rokid Desktop** from the YodaOS launcher. The app auto-connects to the saved IP after a 3-second countdown. Tap **Cancel** to change the IP manually.

On connect, the app also starts `CameraService` in the background — the glasses camera begins streaming JPEG frames to the NUC at `192.168.1.146:8082`.

### 4. Launch via ADB (Windows — BAT scripts included)

```bat
:: NUC_ENTRAR.bat — push stream to glasses
adb shell am force-stop com.rokid.desktop
adb shell svc wifi enable
timeout /t 2 /nobreak >nul
adb shell am start -n com.rokid.desktop/.StreamActivity --es stream_url "http://192.168.1.146:8889/desktop/"
```

```bat
:: NUC_SALIR.bat — return to Rokid launcher
adb shell am force-stop com.rokid.desktop
adb shell am start -n com.rokid.os.sprite.launcher/.main.SpriteMainActivity
```

---

## Headless / Virtual Display Setup

The project uses **Xvfb** on `DISPLAY :1` — a fully virtual display, no physical monitor needed. This is managed by a systemd service called `rokid-session`.

`start.sh` checks if `:1` is available and starts `rokid-session` if not:

```bash
if ! DISPLAY=:1 xdpyinfo >/dev/null 2>&1; then
    systemctl start rokid-session
    sleep 3
fi
```

The desktop background is set to dark grey on start:
```bash
DISPLAY=:1 xsetroot -solid grey20
```

> This approach is simpler and more reliable than using the GDM session (:0) — no XAUTH cookies, no dependency on a logged-in user.

---

## Network Requirements

The stream works **exclusively over local WiFi/LAN**. The glasses (Android WebView) connect directly to the NUC's WebRTC endpoint — there is no VPN support unless Tailscale is installed on the glasses themselves (not tested on YodaOS).

> Both the NUC and the Rokid glasses must be on the same WiFi network.

The camera back-channel also uses direct TCP — the glasses push frames to the hardcoded NUC address (`192.168.1.146:8082`). Update `CameraService.NUC_HOST` if your NUC IP differs.

---

## Project Structure

```
rokid-desktop/
├── server/
│   ├── start.sh               # Starts mediamtx + ffmpeg (DISPLAY :1, 1280×720, 25fps)
│   └── mediamtx.yml           # mediamtx config (WebRTC :8889, HLS :8888, RTSP :8554)
├── apk/
│   ├── gradle.properties      # JDK 21 path + AndroidX config
│   ├── gradlew.bat            # Windows Gradle wrapper
│   └── app/src/main/
│       ├── java/com/rokid/desktop/
│       │   ├── MainActivity.java      # IP selector with 3s auto-connect countdown
│       │   ├── StreamActivity.java    # Fullscreen WebView + starts CameraService
│       │   └── CameraService.java     # Camera2 → JPEG → TCP :8082 to NUC
│       ├── res/layout/
│       │   ├── activity_main.xml
│       │   └── activity_stream.xml
│       └── AndroidManifest.xml        # INTERNET, CAMERA, FOREGROUND_SERVICE_CAMERA
├── hoja-de-ruta.md            # Development log and pending tasks
└── README.md
```

---

## Build the APK

On Linux/Mac:
```bash
cd apk/
./gradlew assembleDebug
```

On Windows:
```bat
cd apk
gradlew.bat assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

> JDK 21 path is set in `gradle.properties` pointing to Android Studio's bundled JDR. Adjust if using a standalone JDK.

---

## Auto-start on Boot (Linux systemd)

```bash
sudo cp server/rokid-desktop.service /etc/systemd/system/
sudo systemctl enable --now rokid-desktop
```

Example service file:

```ini
[Unit]
Description=Rokid Desktop Stream
After=network-online.target rokid-session.target

[Service]
User=root
ExecStart=/home/rokid/desktop-server/start.sh
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

---

## Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| `Display :1 no disponible` | Xvfb / rokid-session not running | `systemctl start rokid-session` then re-run `start.sh` |
| Black screen on glasses (no video) | ffmpeg not capturing | Check `server/stream.log` for x11grab errors |
| App connects but freezes | ICE negotiation failure | Both devices must be on same LAN |
| Camera permission denied | Not granted on first run | Open app settings → grant Camera manually |
| No frames received on NUC :8082 | Wrong NUC IP in CameraService | Update `CameraService.NUC_HOST` to match NUC LAN IP |
| HTTP connection refused on Android | Cleartext traffic blocked | Verify `android:usesCleartextTraffic="true"` in AndroidManifest |
| ADB `am start` fails — activity not found | `exported` flag missing | Verify `android:exported="true"` on `StreamActivity` |
| Video rotated 90° | Wrong screen orientation | `android:screenOrientation="portrait"` in manifest |
| WiFi disabled on glasses | YodaOS WiFi sleep | Run `adb shell svc wifi enable` before launch |

---

## Known Limitations

- **Latency**: ~200–500ms over LAN depending on hardware. Not suitable for video playback or fast-paced content.
- **Audio**: Not streamed — display only.
- **NUC IP hardcoded**: `CameraService.NUC_HOST` is a static constant — requires recompile if the NUC IP changes.
- **Battery**: Continuous WebRTC reception + camera streaming drains glasses battery faster than normal use.
- **API deprecation**: `hideSystemUI()` in `StreamActivity` uses deprecated `SYSTEM_UI_FLAG_*` flags — planned migration to `WindowInsetsController`.

---

> **Warning — Heat & Resource Usage**
>
> Continuous WebRTC streaming + camera capture puts sustained load on the glasses SoC. The Rokid Max / Air 2 Pro will get noticeably warm after 15–20 minutes of use. To reduce heat and extend safe usage time, **lower the capture framerate** in `server/start.sh`:
>
> ```bash
> # Current setting
> -r 25
>
> # Recommended for extended sessions
> -r 15
> ```
>
> A lower framerate also reduces CPU usage on the NUC and improves stream stability on congested WiFi.

---

## Roadmap

- [ ] Systemd `rokid-session.service` — publish service file in repo
- [ ] Dynamic NUC IP discovery (remove hardcoded `192.168.1.146`)
- [ ] Audio streaming via WebRTC (separate audio track in mediamtx)
- [ ] Configurable resolution/bitrate from the APK settings screen
- [ ] Replace deprecated `SYSTEM_UI_FLAG_*` with `WindowInsetsController`
- [ ] Gesture passthrough — map recognized gestures to mouse/keyboard events on the NUC desktop

---

## Related Projects

- [rokid-gestos](https://github.com/PowerKeysolutions/rokid-gestos) — Processes the camera stream received on `:8082` using MediaPipe Hands to detect gestures and translate them to mouse/keyboard events via xdotool.

---

## Contributing

Issues and PRs are welcome. For significant changes, open an issue first to discuss the approach.

**Dev environment:**
- Android Studio Hedgehog+ for the APK
- Any Linux box with ffmpeg + Xvfb + mediamtx for server-side testing
- Rokid glasses with USB debugging enabled for device testing

---

## License

MIT
