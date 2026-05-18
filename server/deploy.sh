#!/bin/bash
# Configure your NUC address before running (Tailscale IP or LAN IP)
NUC="${NUC_HOST:-root@<NUC-IP>}"
REMOTE="/home/rokid/desktop-server"

echo "Desplegando en NUC..."
scp mediamtx.yml start.sh "$NUC:$REMOTE/"
ssh "$NUC" "chmod +x $REMOTE/start.sh"
echo "Listo. Para arrancar el stream:"
echo "  ssh $NUC '$REMOTE/start.sh'"
