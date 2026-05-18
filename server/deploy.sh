#!/bin/bash
NUC="root@NUC-IP"
REMOTE="/home/rokid/desktop-server"

echo "Desplegando en NUC..."
scp mediamtx.yml start.sh "$NUC:$REMOTE/"
ssh "$NUC" "chmod +x $REMOTE/start.sh"
echo "Listo. Para arrancar el stream:"
echo "  ssh $NUC '$REMOTE/start.sh'"
