# rokid-desktop

Stream the desktop of a Linux PC to **Rokid AR glasses** in real time via WebRTC.

> Tested on: Rokid Max / Air 2 Pro — YodaOS (Android 12, SDK 32) — NUC running Ubuntu/GNOME

---

## How it works

```
Linux PC (NUC)                        Rokid Glasses (Android 12)
──────────────────────────────────    ──────────────────────────
ffmpeg x11grab → RTSP :8554       →   mediamtx WebRTC :8889
mediamtx → WebRTC + HLS           →   APK WebView (fullscreen)
```

The APK opens a fullscreen **WebView** that loads mediamtx's built-in JavaScript WebRTC player. No native WebRTC SDK needed — the browser engine handles everything.

The Rokid display is **additive** (black pixels = transparent). Setting the desktop background to pure black makes the stream appear as an AR overlay on the real world.

---

## Requirements

### Linux PC (server)
- `ffmpeg` with x11grab support
- `mediamtx` v1.18+ — [releases](https://github.com/bluenviron/mediamtx/releases)
- X11 display (GNOME/KDE/etc.)

### Rokid Glasses
- USB debugging enabled
- `adb` on the control PC
- WiFi on same LAN as the Linux PC

---

## Quick Start

### 1. Linux PC — start the stream

```bash
cd server/
./start.sh
```

Stream will be available at:
- **WebRTC**: `http://<NUC-IP>:8889/desktop/`
- **HLS**: `http://<NUC-IP>:8888/desktop/index.m3u8`

### 2. Install APK on glasses

```bash
adb install -r apk/app/build/outputs/apk/debug/app-debug.apk
```

### 3. Launch from glasses

Open **Rokid Desktop** from the YodaOS launcher. The app auto-connects to the saved IP after a 3-second countdown. Tap **Cancel** to change the IP manually.

### 4. Launch via ADB (Windows BAT scripts included)

```bat
# NUC_ENTRAR.bat — start stream on glasses
adb shell am force-stop com.rokid.desktop
adb shell svc wifi enable
timeout /t 2 /nobreak >nul
adb shell am start -n com.rokid.desktop/.StreamActivity --es stream_url "http://192.168.1.146:8889/desktop/"
```

```bat
# NUC_SALIR.bat — return to Rokid menu
adb shell am force-stop com.rokid.desktop
adb shell am start -n com.rokid.os.sprite.launcher/.main.SpriteMainActivity
```

---

## Project Structure

```
rokid-desktop/
├── server/
│   ├── start.sh          # Starts mediamtx + ffmpeg on the Linux PC
│   └── mediamtx.yml      # mediamtx config (WebRTC :8889, HLS :8888, RTSP :8554)
└── apk/
    └── app/src/main/
        ├── java/com/rokid/desktop/
        │   ├── MainActivity.java      # IP selector with auto-connect countdown
        │   └── StreamActivity.java    # Fullscreen WebView player
        ├── res/layout/
        │   ├── activity_main.xml
        │   └── activity_stream.xml
        └── AndroidManifest.xml
```

---

## Build the APK

Requires Android SDK (or Android Studio):

```bash
cd apk/
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

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
After=network-online.target

[Service]
User=root
ExecStartPre=/bin/sleep 5
ExecStart=/home/rokid/desktop-server/start.sh
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

---

## Key fixes (lessons learned)

| Problem | Fix |
|--------|-----|
| HTTP blocked on Android 9+ | `android:usesCleartextTraffic="true"` in AndroidManifest |
| Activity not launchable via ADB | `android:exported="true"` on StreamActivity |
| Video appears sideways | `android:screenOrientation="portrait"` |
| WiFi not active on glasses | `adb shell svc wifi enable` before launch |
| Native WebRTC SDK ICE failures | Replaced with WebView + mediamtx JS player |
| Desktop background not black | `xsetroot -solid black` + PNG wallpaper in start.sh |

---

## Related project — Hand gesture control

[rokid-gestos](https://github.com/PowerKey/rokid-gestos) — Control the Linux desktop with hand gestures captured by the glasses camera:
- Glasses camera → MJPEG HTTP stream → NUC
- MediaPipe Hands on NUC → gesture recognition
- xdotool → mouse/keyboard events

---

## License

MIT
