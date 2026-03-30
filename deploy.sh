#!/usr/bin/env bash
set -e

EC2_IP="54.176.73.11"
SSH_KEY="$HOME/Downloads/usrd.pem"
SSH_USER="ubuntu"
REMOTE_DIR="/home/ubuntu/wa-mirror"
PORT=7070

echo "=== WA Mirror Deploy ==="
echo "Target: $SSH_USER@$EC2_IP:$REMOTE_DIR"
echo ""

# Verify key exists
if [ ! -f "$SSH_KEY" ]; then
  echo "ERROR: SSH key not found at $SSH_KEY"
  exit 1
fi
chmod 600 "$SSH_KEY"

SSH="ssh -i $SSH_KEY -o StrictHostKeyChecking=no $SSH_USER@$EC2_IP"
SCP="scp -i $SSH_KEY -o StrictHostKeyChecking=no"

echo "[1/5] Creating remote directory..."
$SSH "mkdir -p $REMOTE_DIR"

echo "[2/5] Copying server files..."
$SCP server/app.py      "$SSH_USER@$EC2_IP:$REMOTE_DIR/app.py"
$SCP server/requirements.txt "$SSH_USER@$EC2_IP:$REMOTE_DIR/requirements.txt"
$SCP server/index.html  "$SSH_USER@$EC2_IP:$REMOTE_DIR/index.html"
$SCP server/sw.js       "$SSH_USER@$EC2_IP:$REMOTE_DIR/sw.js"

echo "[3/5] Installing Python dependencies..."
$SSH "cd $REMOTE_DIR && pip3 install -r requirements.txt --quiet"

echo "[4/5] Creating systemd service..."
$SSH "sudo tee /etc/systemd/system/wa-mirror.service > /dev/null << 'UNIT'
[Unit]
Description=WA Mirror Notification Server
After=network.target

[Service]
User=ubuntu
WorkingDirectory=$REMOTE_DIR
ExecStart=/usr/bin/python3 $REMOTE_DIR/app.py
Restart=always
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
UNIT"

$SSH "sudo systemctl daemon-reload && sudo systemctl enable wa-mirror && sudo systemctl restart wa-mirror"

echo "[5/5] Checking service status..."
sleep 2
$SSH "sudo systemctl status wa-mirror --no-pager -l" || true

echo ""
echo "========================================"
echo "  REMINDER: Ensure port $PORT is open"
echo "  in your EC2 Security Group (TCP inbound)"
echo "========================================"
echo ""
echo "  PWA URL: http://$EC2_IP:$PORT"
echo "========================================"
