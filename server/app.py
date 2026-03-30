import os
import json
from flask import Flask, request, jsonify, send_from_directory
from flask_socketio import SocketIO
from pywebpush import webpush, WebPushException

app = Flask(__name__)
socketio = SocketIO(app, cors_allowed_origins="*")

SECRET_KEY = "Vmos@123"

VAPID_PRIVATE_KEY = """-----BEGIN PRIVATE KEY-----
MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg2KswYntRie+RcDq8
T7tUryLzaaCRLig9JAhfSbuqxCuhRANCAATEQTS1ZvXmfi9CedTU/HMlOQ6hJGBR
oIJ8Gxl8PTw8JS+uM++DhS2LafM1utCbdWmwglBSky96xXfQ2qveHEgr
-----END PRIVATE KEY-----"""

VAPID_PUBLIC_KEY = "BMRBNLVm9eZ-L0J51NT8cyU5DqEkYFGggnwbGXw9PDwlL64z74OFLYtp8zW60Jt1abCCUFKTL3rFd9Daq94cSCs"
VAPID_CLAIMS = {"sub": "mailto:admin@wamirror.local"}

# In-memory push subscription store (no persistence)
push_subscription = None


def check_secret(data):
    return data.get("secret") == SECRET_KEY


@app.route("/")
def index():
    return send_from_directory(os.path.dirname(__file__), "index.html")


@app.route("/sw.js")
def service_worker():
    response = send_from_directory(os.path.dirname(__file__), "sw.js")
    response.headers["Content-Type"] = "application/javascript"
    response.headers["Service-Worker-Allowed"] = "/"
    return response


@app.route("/vapid-public-key")
def vapid_public_key():
    return jsonify({"publicKey": VAPID_PUBLIC_KEY})


@app.route("/subscribe", methods=["POST"])
def subscribe():
    global push_subscription
    data = request.get_json(silent=True) or {}
    if not data.get("endpoint"):
        return jsonify({"error": "invalid subscription"}), 400
    push_subscription = data
    return jsonify({"status": "subscribed"}), 201


@app.route("/notify", methods=["POST"])
def notify():
    global push_subscription
    data = request.get_json(silent=True) or {}

    if not check_secret(data):
        return jsonify({"error": "unauthorized"}), 401

    app_name = data.get("app", "WhatsApp")
    sender = data.get("sender", "Unknown")
    message = data.get("message", "")
    timestamp = data.get("timestamp", "")

    if not push_subscription:
        return jsonify({"status": "no_subscriber"}), 202

    payload = json.dumps({
        "title": f"{app_name}: {sender}",
        "body": message,
        "timestamp": timestamp
    })

    try:
        webpush(
            subscription_info=push_subscription,
            data=payload,
            vapid_private_key=VAPID_PRIVATE_KEY,
            vapid_claims=VAPID_CLAIMS
        )
        return jsonify({"status": "sent"}), 200
    except WebPushException as e:
        if e.response and e.response.status_code in (404, 410):
            push_subscription = None
        return jsonify({"status": "push_failed"}), 500


if __name__ == "__main__":
    print("WA Mirror server starting on port 7070")
    socketio.run(app, host="0.0.0.0", port=7070)
