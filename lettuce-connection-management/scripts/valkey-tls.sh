#!/usr/bin/env bash
# 在 WSL (Podman) 中创建/销毁用于 TLS 集成测试的 valkey（单机 + TLS，默认端口 6380）。
#
# 用法（在 WSL 中执行）：
#   bash scripts/valkey-tls.sh up    生成自签证书、启动 TLS 节点，并导出 CA 证书到 target/tls/
#   bash scripts/valkey-tls.sh down  销毁容器（保留证书）
#
# 证书存放（WSL）：~/.valkey-tls/{ca.crt,ca.key,server.crt,server.key}
# 导出的 CA 证书（Windows 侧测试使用）：lettuce-connection-management/target/tls/ca.crt
set -euo pipefail

IMAGE="${VALKEY_IMAGE:-docker.io/valkey/valkey:9.1.1}"
PASSWORD="${VALKEY_PASSWORD:-change_me}"
TLS_PORT="${VALKEY_TLS_PORT:-6380}"
CONTAINER="valkey-tls"
CERT_DIR="${HOME}/.valkey-tls"
REPO_TLS_DIR="/mnt/d/Workspaces/lettuce-katas/lettuce-connection-management/target/tls"

require_openssl() {
  if ! command -v openssl >/dev/null 2>&1; then
    echo "openssl is required to generate test certificates" >&2
    exit 1
  fi
}

generate_certificates() {
  if [ -f "${CERT_DIR}/ca.crt" ] && [ -f "${CERT_DIR}/server.crt" ]; then
    echo "certificates already exist in ${CERT_DIR}"
    return
  fi
  require_openssl
  mkdir -p "${CERT_DIR}"

  openssl genrsa -out "${CERT_DIR}/ca.key" 4096 >/dev/null 2>&1
  openssl req -x509 -new -nodes -key "${CERT_DIR}/ca.key" -sha256 -days 3650 \
    -subj "/CN=valkey-test-ca" -out "${CERT_DIR}/ca.crt" >/dev/null 2>&1

  openssl genrsa -out "${CERT_DIR}/server.key" 2048 >/dev/null 2>&1
  openssl req -new -key "${CERT_DIR}/server.key" -subj "/CN=localhost" \
    -out "${CERT_DIR}/server.csr" >/dev/null 2>&1
  printf 'subjectAltName=DNS:localhost,IP:127.0.0.1\n' > "${CERT_DIR}/server.ext"
  openssl x509 -req -in "${CERT_DIR}/server.csr" -CA "${CERT_DIR}/ca.crt" \
    -CAkey "${CERT_DIR}/ca.key" -CAcreateserial -out "${CERT_DIR}/server.crt" \
    -days 3650 -sha256 -extfile "${CERT_DIR}/server.ext" >/dev/null 2>&1

  echo "test certificates generated in ${CERT_DIR}"
}

up() {
  generate_certificates
  # 容器 entrypoint 会把 valkey-server 降权到 valkey 用户运行，测试证书需要可被该用户读取
  chmod 644 "${CERT_DIR}"/*.crt "${CERT_DIR}"/*.key
  podman rm -f "${CONTAINER}" >/dev/null 2>&1 || true

  podman run -d --name "${CONTAINER}" --network=host \
    -v "${CERT_DIR}:/tls:ro" \
    "${IMAGE}" valkey-server \
      --port 0 \
      --tls-port "${TLS_PORT}" \
      --bind 127.0.0.1 \
      --tls-cert-file /tls/server.crt \
      --tls-key-file /tls/server.key \
      --tls-ca-cert-file /tls/ca.crt \
      --tls-auth-clients no \
      --requirepass "${PASSWORD}" \
      --save '' >/dev/null

  local attempt
  for attempt in $(seq 1 40); do
    if podman exec "${CONTAINER}" valkey-cli --tls --cacert /tls/ca.crt \
        -p "${TLS_PORT}" -a "${PASSWORD}" --no-auth-warning ping >/dev/null 2>&1; then
      break
    fi
    if [ "${attempt}" = "40" ]; then
      echo "TLS valkey did not become ready" >&2
      exit 1
    fi
    sleep 0.5
  done

  mkdir -p "${REPO_TLS_DIR}"
  cp "${CERT_DIR}/ca.crt" "${REPO_TLS_DIR}/ca.crt"
  echo "TLS valkey ready on 127.0.0.1:${TLS_PORT}; CA certificate exported to ${REPO_TLS_DIR}/ca.crt"
}

down() {
  podman rm -f "${CONTAINER}" >/dev/null 2>&1 || true
  echo "TLS valkey container removed (certificates kept in ${CERT_DIR})"
}

case "${1:-up}" in
  up) up ;;
  down) down ;;
  *)
    echo "usage: $0 {up|down}" >&2
    exit 2
    ;;
esac