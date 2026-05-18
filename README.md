# rokid-desktop

Stream the desktop of a Linux PC to **Rokid AR glasses** in real time via WebRTC.

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
- [Headless / No-Monitor Setup](#headless--no-monitor-setup)
- [Remote Access via VPN](#remote-access-via-vpn)
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

```
Linux PC (NUC)                        Rokid Glasses (Android 12)
──────────────────────────────────    ──────────────────────────
ffmpeg x11grab → RTSP :8554       →   mediamtx WHEP :8889
mediamtx → WebRTC + HLS           →   APK WebView (fullscreen)
```

The APK opens a fullscreen **WebView** that loads mediamtx's built-in JavaScript WebRTC player. No native WebRTC SDK needed — the browser engine handles everything.

The Rokid display is **additive** (black pixels = transparent). Setting the desktop background to pure black makes the stream appear as an AR overlay on the real world.

---

## Requirements

### Linux PC (server)
- `ffmpeg` with x11grab support (`apt install ffmpeg`)
- `mediamtx` v1.18+ — [releases](https://github.com/bluenviron/mediamtx/releases)
- X11 display (GNOME/KDE/etc.) — see [Headless setup](#headless--no-monitor-setup) if running without monitor

### Rokid Glasses
- USB debugging enabled (Settings → Developer options)
- `adb` installed on the control PC
- WiFi on same LAN as the Linux PC (or reachable via VPN)

### Build dependencies (APK only)
- Android Studio or JDK 21 + Android SDK
- Gradle (wrapper included)

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

### 2. Install APK on glasses

```bash
adb install -r apk/app/build/outputs/apk/debug/app-debug.apk
```

### 3. Launch from glasses

Open **Rokid Desktop** from the YodaOS launcher. The app auto-connects to the saved IP after a 3-second countdown. Tap **Cancel** to change the IP manually.

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

## Headless / No-Monitor Setup

Running the NUC **without a physical monitor** (common for always-on setups) requires a valid X11 session and XAUTH cookie. The included `start.sh` handles this automatically using the GDM session:

```bash
# start.sh uses the GDM Xauthority cookie
export XAUTHORITY=/run/user/1000/gdm/Xauthority
export DISPLAY=:0

# Verify X11 is accessible before starting
xdpyinfo -display :0 >/dev/null 2>&1 || { echo "No X11 display"; exit 1; }
```

**Requirements for headless:**
- A user must be logged in (GNOME session active) — GDM auto-login recommended
- `/run/user/1000/gdm/Xauthority` must exist (replace `1000` with your UID if different)
- Minimum resolution: 1024×768 (set in GNOME display settings or via `xrandr`)

**Set a black background on session start:**
```bash
xsetroot -solid black
```
This makes the stream invisible on the Rokid overlay instead of showing a bright background.

---

## Remote Access via VPN

If the glasses and NUC are on different networks (e.g., NUC at office, glasses on mobile), you can use **Tailscale** or WireGuard:

```bash
# Replace LAN IP with Tailscale IP
adb shell am start -n com.rokid.desktop/.StreamActivity \
  --es stream_url "http://NUC-TAILSCALE-IP:8889/desktop/"
```

> Note: WebRTC over VPN may have higher latency. For best results use LAN.

---

## Project Structure

```
rokid-desktop/
├── server/
│   ├── start.sh               # Starts mediamtx + ffmpeg capture
│   └── mediamtx.yml           # mediamtx config (WebRTC :8889, HLS :8888, RTSP :8554)
├── apk/
│   └── app/src/main/
│       ├── java/com/rokid/desktop/
│       │   ├── MainActivity.java      # IP selector with 3s auto-connect countdown
│       │   └── StreamActivity.java    # Fullscreen WebView WHEP player
│       ├── res/layout/
│       │   ├── activity_main.xml
│       │   └── activity_stream.xml
│       └── AndroidManifest.xml
├── hoja-de-ruta.md            # Development log and pending tasks
└── README.md
```

---

## Build the APK

Requires Android SDK or Android Studio with JDK 21:

```bash
cd apk/
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

On Windows:
```bat
cd apk
gradlew.bat assembleDebug
```

> JDK 21 path must be set in `gradle.properties` if not using Android Studio's bundled JDK.

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
After=network-online.target graphical-session.target

[Service]
User=root
Environment=DISPLAY=:0
Environment=XAUTHORITY=/run/user/1000/gdm/Xauthority
ExecStartPre=/bin/sleep 5
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
| `No X11 display` error | XAUTH cookie missing or wrong path | Check `/run/user/1000/gdm/Xauthority` exists; verify UID with `id` |
| Black screen on glasses (no video) | ffmpeg not capturing | Run `start.sh` manually and check stderr for `x11grab` errors |
| App connects but freezes | ICE negotiation failure | Both devices must be on same LAN or connected via VPN |
| HTTP connection refused on Android | Cleartext traffic blocked | Verify `android:usesCleartextTraffic="true"` in AndroidManifest |
| ADB `am start` fails — activity not found | `exported` flag missing | Verify `android:exported="true"` on `StreamActivity` |
| Video rotated 90° | Wrong screen orientation | `android:screenOrientation="portrait"` in manifest |
| WiFi disabled on glasses | YodaOS WiFi sleep | Run `adb shell svc wifi enable` before launching app |
| Low resolution headless | GNOME virtual display default | Set display resolution with `xrandr --newmode` or GNOME settings |
| WebRTC ICE fails (native SDK) | Rokid SDK incompatibility | Use WebView + mediamtx JS player (already implemented) |

---

## Known Limitations

- **Latency**: ~200–500ms over LAN depending on hardware. Not suitable for video playback or fast-paced content.
- **Audio**: Not streamed — display only.
- **Resolution**: Rokid Max native resolution is 1920×1080 per eye; desktop resolution should match for best clarity.
- **Battery**: Continuous WebRTC reception drains glasses battery faster than normal use.
- **API deprecation**: `WindowInsetsController` usage in `StreamActivity` targets a deprecated API path — planned update in roadmap.

---

## Roadmap

- [ ] Systemd service with automatic XAUTH discovery (no hardcoded UID)
- [ ] Audio streaming via WebRTC (separate audio track in mediamtx)
- [ ] Configurable resolution/bitrate from the APK settings screen
- [ ] Replace deprecated `WindowInsetsController` with current insets API
- [ ] Gesture passthrough — tap on glasses triggers mouse click on desktop
- [ ] Multi-source selection (switch between desktop streams from multiple PCs)

---

## Related Projects

- [rokid-gestos](https://github.com/PowerKeysolutions/rokid-gestos) — Control the Linux desktop with hand gestures captured by the glasses camera (MediaPipe → xdotool)

---

## Contributing

Issues and PRs are welcome. For significant changes, open an issue first to discuss the approach.

**Dev environment:**
- Android Studio Hedgehog+ for the APK
- Any Linux box with ffmpeg + mediamtx for server-side testing
- Rokid glasses with USB debugging enabled for device testing

---

## License

MIT
