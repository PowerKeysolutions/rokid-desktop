#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG="$SCRIPT_DIR/stream.log"

find_xauth() {
    for f in /run/user/*/gdm/Xauthority /run/user/*/Xauthority; do
        [ -f "$f" ] && echo "$f" && return
    done
    for f in /home/*/.Xauthority /root/.Xauthority; do
        [ -f "$f" ] && echo "$f" && return
    done
}

XAUTH=$(find_xauth)
DISP=":0"

if [ -n "$XAUTH" ] && DISPLAY=:0 XAUTHORITY="$XAUTH" xdpyinfo >/dev/null 2>&1; then
    RES=$(DISPLAY=:0 XAUTHORITY="$XAUTH" xdpyinfo 2>/dev/null | grep dimensions | awk '{print $2}' | head -1)
    [ -z "$RES" ] && RES="1920x1080"
    echo "✓ X11 display :0 activo ($RES)"
else
    echo "No hay display X11, arrancando Xvfb :99..."
    pkill Xvfb 2>/dev/null; sleep 0.5
    Xvfb :99 -screen 0 1920x1080x24 &
    sleep 1
    DISP=":99"
    XAUTH=""
    RES="1920x1080"
fi

# Forzar fondo negro (AR display: negro = transparente)
if [ -n "$XAUTH" ]; then
    DISPLAY=$DISP XAUTHORITY=$XAUTH xsetroot -solid black 2>/dev/null || true
    GNOME_PID=$(pgrep -u usuario gnome-shell 2>/dev/null | head -1)
    if [ -n "$GNOME_PID" ]; then
        DBUS=$(cat /proc/$GNOME_PID/environ 2>/dev/null | tr '\0' '\n' | grep "^DBUS_SESSION_BUS_ADDRESS=" | cut -d= -f2-)
        [ -n "$DBUS" ] && su usuario -s /bin/bash -c \
            "HOME=/home/usuario DBUS_SESSION_BUS_ADDRESS='$DBUS' gsettings set org.gnome.desktop.background picture-uri file:///home/usuario/black.png; gsettings set org.gnome.desktop.background picture-options scaled" 2>/dev/null || true
    fi
fi

# Detener procesos previos
pkill -f "mediamtx" 2>/dev/null
pkill -f "x11grab" 2>/dev/null
sleep 1

# Arrancar mediamtx
echo "Arrancando mediamtx..."
cd "$SCRIPT_DIR"
./mediamtx mediamtx.yml >> "$LOG" 2>&1 &
MEDIAMTX_PID=$!
sleep 2

cleanup() {
    kill $MEDIAMTX_PID 2>/dev/null
    pkill -f "x11grab" 2>/dev/null
    exit 0
}
trap cleanup SIGINT SIGTERM

# Arrancar ffmpeg
echo "Capturando $RES en $DISP..."
env DISPLAY=$DISP ${XAUTH:+XAUTHORITY=$XAUTH} \
    ffmpeg -f x11grab -r 25 -video_size "$RES" -i "$DISP" \
    -c:v libx264 -preset ultrafast -tune zerolatency \
    -pix_fmt yuv420p -b:v 3M -maxrate 3M -bufsize 6M \
    -g 50 -f rtsp rtsp://localhost:8554/desktop \
    -loglevel warning >> "$LOG" 2>&1 &

NUC_IP=$(hostname -I | awk '{print $1}')
echo ""
echo "✓ Stream activo — IP del NUC: $NUC_IP"
echo "  WebRTC player: http://$NUC_IP:8889/desktop/"
echo "  HLS:           http://$NUC_IP:8888/desktop/index.m3u8"
echo "  Log:           $LOG"
echo ""
echo "Ctrl+C para parar"
wait $MEDIAMTX_PID
