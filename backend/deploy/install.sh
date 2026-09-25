#!/usr/bin/env bash
set -euo pipefail

SOURCE_DIR="$(cd "$(dirname "$0")/.." && pwd)"

if ! id cubus >/dev/null 2>&1; then
    useradd --system --home-dir /opt/cubus --shell /usr/sbin/nologin cubus
fi

install -d -o cubus -g cubus -m 0750 /opt/cubus
cp -a "$SOURCE_DIR/app" /opt/cubus/
cp "$SOURCE_DIR/requirements.txt" /opt/cubus/
if [ ! -f /opt/cubus/.env ]; then
    cp "$SOURCE_DIR/.env" /opt/cubus/.env
fi

update_env_value() {
    local key="$1"
    local value
    local temporary
    value="$(grep -m1 "^${key}=" "$SOURCE_DIR/.env" | cut -d= -f2-)"
    [ -n "$value" ] || return 0
    temporary="$(mktemp)"
    grep -v "^${key}=" /opt/cubus/.env > "$temporary" || true
    printf '%s=%s\n' "$key" "$value" >> "$temporary"
    install -o cubus -g cubus -m 0600 "$temporary" /opt/cubus/.env
    rm -f "$temporary"
}

for key in \
    PUBLIC_BASE_URL \
    UPDATE_APK_PATH \
    UPDATE_VERSION_CODE \
    UPDATE_VERSION_NAME \
    UPDATE_MANDATORY \
    UPDATE_RELEASE_NOTES \
    STATISTICS_SCHEDULER_ENABLED
do
    update_env_value "$key"
done

install -d -o cubus -g cubus -m 0750 /opt/cubus/server_documents
install -d -o cubus -g cubus -m 0750 /opt/cubus/server_auth
install -d -o cubus -g cubus -m 0750 /opt/cubus/server_registry
install -d -o cubus -g cubus -m 0750 /opt/cubus/releases
if [ -f "$SOURCE_DIR/releases/CUBUS-latest.apk" ]; then
    install -o cubus -g cubus -m 0644 \
        "$SOURCE_DIR/releases/CUBUS-latest.apk" \
        /opt/cubus/releases/CUBUS-latest.apk
fi
chown -R cubus:cubus /opt/cubus
chmod 0600 /opt/cubus/.env

python3 -m venv /opt/cubus/.venv
/opt/cubus/.venv/bin/pip install --disable-pip-version-check --no-cache-dir -r /opt/cubus/requirements.txt
chown -R cubus:cubus /opt/cubus/.venv

install -m 0644 "$SOURCE_DIR/deploy/cubus.service" /etc/systemd/system/cubus.service
install -m 0644 "$SOURCE_DIR/deploy/Caddyfile" /etc/caddy/Caddyfile

systemctl daemon-reload
systemctl enable cubus
systemctl restart cubus
caddy validate --config /etc/caddy/Caddyfile
systemctl reload caddy

sleep 2
curl --fail --silent http://127.0.0.1:8000/health
echo
echo "CUBUS server installation completed."
