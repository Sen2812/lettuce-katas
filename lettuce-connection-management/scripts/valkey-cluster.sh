#!/usr/bin/env bash
# 在 WSL (Podman) 中创建/检查/销毁集成测试用 valkey 集群（默认 3 主 3 从）。
#
# 用法（在 WSL 中执行）：
#   bash scripts/valkey-cluster.sh up      创建集群
#   bash scripts/valkey-cluster.sh status  查看集群状态与节点分配
#   bash scripts/valkey-cluster.sh down    销毁集群
#
# 设计说明：
#   * 所有节点以 host 网络运行在 WSL 的 127.0.0.1 上（端口 7001-7006，总线端口 17001-17006），
#     Windows 侧客户端经 WSL 的 localhost 转发直连 127.0.0.1:700X。
#   * cluster-announce-ip 固定为 127.0.0.1，保证拓扑中返回的地址在 Windows 侧同样可达。
set -euo pipefail

IMAGE="${VALKEY_IMAGE:-docker.io/valkey/valkey:9.1.1}"
PASSWORD="${VALKEY_PASSWORD:-change_me}"
BASE_PORT="${VALKEY_BASE_PORT:-7001}"
NODE_COUNT="${VALKEY_NODE_COUNT:-6}"

node_name() { echo "valkey-node-$1"; }

ports() {
  local i
  for ((i = 0; i < NODE_COUNT; i++)); do
    echo $((BASE_PORT + i))
  done
}

port_list() {
  local list="" sep="" port
  for port in $(ports); do
    list="${list}${sep}127.0.0.1:${port}"
    sep=" "
  done
  echo "${list}"
}

wait_ready() {
  local port attempt
  for port in $(ports); do
    for attempt in $(seq 1 60); do
      if podman exec "$(node_name "${port}")" valkey-cli -p "${port}" -a "${PASSWORD}" --no-auth-warning ping >/dev/null 2>&1; then
        break
      fi
      if [ "${attempt}" = "60" ]; then
        echo "node on port ${port} did not become ready" >&2
        exit 1
      fi
      sleep 0.5
    done
  done
}

up() {
  local port bus name
  for port in $(ports); do
    name="$(node_name "${port}")"
    bus=$((port + 10000))
    podman rm -f "${name}" >/dev/null 2>&1 || true
    podman run -d --name "${name}" --network=host "${IMAGE}" \
      valkey-server \
      --port "${port}" \
      --bind 127.0.0.1 \
      --cluster-enabled yes \
      --cluster-config-file "nodes-${port}.conf" \
      --cluster-node-timeout 5000 \
      --cluster-announce-ip 127.0.0.1 \
      --cluster-announce-port "${port}" \
      --cluster-announce-bus-port "${bus}" \
      --requirepass "${PASSWORD}" \
      --masterauth "${PASSWORD}" \
      --appendonly no \
      --save '' >/dev/null
  done

  wait_ready
  echo "all ${NODE_COUNT} nodes are up, creating cluster ..."

  # shellcheck disable=SC2086
  podman exec "$(node_name "${BASE_PORT}")" \
    valkey-cli -a "${PASSWORD}" --no-auth-warning \
    --cluster create $(port_list) --cluster-replicas 1 --cluster-yes

  echo
  echo "cluster ready. seed for Windows-side clients: 127.0.0.1:${BASE_PORT}"
}

status() {
  podman exec "$(node_name "${BASE_PORT}")" \
    valkey-cli -p "${BASE_PORT}" -a "${PASSWORD}" --no-auth-warning cluster info
  echo
  podman exec "$(node_name "${BASE_PORT}")" \
    valkey-cli -p "${BASE_PORT}" -a "${PASSWORD}" --no-auth-warning cluster nodes
}

down() {
  local port
  for port in $(ports); do
    podman rm -f "$(node_name "${port}")" >/dev/null 2>&1 || true
  done
  echo "valkey cluster containers removed"
}

case "${1:-up}" in
  up) up ;;
  status) status ;;
  down) down ;;
  *)
    echo "usage: $0 {up|status|down}" >&2
    exit 2
    ;;
esac