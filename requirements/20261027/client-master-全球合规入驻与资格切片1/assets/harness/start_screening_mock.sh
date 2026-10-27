#!/usr/bin/env bash
# 本机启动 / 停止合成筛查服务（S_SCREENING_MOCK 出站地址）。
#   start [port]   默认 18191；运行目录 <主仓库>/local-environment/e2e/runtime/screening-mock/（pid、日志、流水）
#   stop | status
# 本机 channel-connector 须带 CHANNELCONNECTOR_PROTOCOL_EXECUTION_ALLOWEDSCHEMES=https,http 与
# CHANNELCONNECTOR_PROTOCOL_EXECUTION_TRUSTEDNETWORKS=127.0.0.1/32（本机 e2e 已如此）。
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
MAIN_REPO="${CODE_MOR_MAIN_REPO:-/Users/kenny/Desktop/Workspace/Code-MoR}"
RUN="$MAIN_REPO/local-environment/e2e/runtime/screening-mock"
PORT="${2:-18191}"
mkdir -p "$RUN"
case "${1:-start}" in
  start)
    if [[ -f "$RUN/mock.pid" ]] && kill -0 "$(cat "$RUN/mock.pid")" 2>/dev/null; then
      echo "already running pid $(cat "$RUN/mock.pid") port $(cat "$RUN/port" 2>/dev/null)"; exit 0
    fi
    nohup python3 -I "$HERE/screening_mock_server.py" --host 127.0.0.1 --port "$PORT" --journal "$RUN/journal.jsonl" \
      >"$RUN/stdout.log" 2>&1 &
    echo $! >"$RUN/mock.pid"; echo "$PORT" >"$RUN/port"
    for _ in $(seq 1 50); do
      curl -fsS "http://127.0.0.1:$PORT/health" >/dev/null 2>&1 && { echo "started pid $(cat "$RUN/mock.pid") port $PORT"; exit 0; }
      sleep 0.1
    done
    echo "screening mock failed to start; see $RUN/stdout.log" >&2; exit 1 ;;
  stop)
    [[ -f "$RUN/mock.pid" ]] && kill "$(cat "$RUN/mock.pid")" 2>/dev/null || true
    rm -f "$RUN/mock.pid"; echo stopped ;;
  status)
    if [[ -f "$RUN/mock.pid" ]] && kill -0 "$(cat "$RUN/mock.pid")" 2>/dev/null; then
      echo "running pid $(cat "$RUN/mock.pid") port $(cat "$RUN/port")"; else echo "not running"; exit 1; fi ;;
  *) echo "usage: $0 start [port] | stop | status" >&2; exit 2 ;;
esac
