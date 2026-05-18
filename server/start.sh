#!/bin/bash
# Stream del escritorio de rokid (DISPLAY :1, Xvfb) a las gafas via WebRTC
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG="$SCRIPT_DIR/stream.log"
DISP=":1"
RES="1280x720"

# Asegurar que el display :1 esté listo (arrancado por rokid-session.service)
if ! DISPLAY=:1 xdpyinfo >/dev/null 2>&1; then
    echo "Display :1 no disponible, arrancándolo..."
    systemctl start rokid-session 2>/dev/null || true
    sleep 3
fi

# Fondo gris oscuro en el display de rokid (negro = transparente en AR)
DISPLAY=:1 xsetroot -solid grey20 2>/dev/null || true

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

# Arrancar ffmpeg capturando DISPLAY :1 (escritorio de rokid)
echo "Capturando $RES en $DISP..."
DISPLAY=$DISP ffmpeg -f x11grab -r 25 -video_size "$RES" -i "$DISP" \
    -c:v libx264 -preset ultrafast -tune zerolatency \
    -pix_fmt yuv420p \
    -colorspace 1 -color_primaries 1 -color_trc 1 \
    -b:v 5M -maxrate 5M -bufsize 10M \
    -g 50 -f rtsp rtsp://localhost:8554/desktop \
    -loglevel warning >> "$LOG" 2>&1 &

NUC_IP=$(hostname -I | awk '{print $1}')
echo ""
echo "✓ Stream activo (usuario rokid, DISPLAY :1) — IP del NUC: $NUC_IP"
echo "  WebRTC player: http://$NUC_IP:8889/desktop/"
echo "  HLS:           http://$NUC_IP:8888/desktop/index.m3u8"
echo "  Log:           $LOG"
echo ""
echo "Ctrl+C para parar"
wait $MEDIAMTX_PID
