#!/usr/bin/env bash
# ============================================================
# 一键重启后端（gateway / user / course / biz 四个 jar）
# 依赖：init-server.sh 已装好 systemd 单元
# 执行：bash /opt/schoolhelp/restart-backend.sh
# ============================================================
set -uo pipefail

SERVICES="gateway user course biz"
APP=/opt/schoolhelp/app
PIDS_PORTS="8080 8101 8102 8103"

log() { echo -e "\033[36m[backend]\033[0m $*"; }
ok()  { echo -e "\033[32m[ok]\033[0m $*"; }
err() { echo -e "\033[31m[err]\033[0m $*" >&2; }

# 校验 jar 是否齐全
for s in $SERVICES; do
  if [ ! -f "$APP/schoolhelp-$s.jar" ]; then
    err "缺少 $APP/schoolhelp-$s.jar，请先执行 pull-build-deploy.sh 打包"
    exit 1
  fi
done

log "重启服务：$SERVICES"
systemctl daemon-reload
systemctl restart schoolhelp-gateway schoolhelp-user schoolhelp-course schoolhelp-biz

log "等待端口就绪 ..."
for i in $(seq 1 30); do
  sleep 2
  up=0
  for p in $PIDS_PORTS; do
    if ss -ltn 2>/dev/null | grep -q ":$p "; then up=$((up+1)); fi
  done
  if [ "$up" -eq 4 ]; then break; fi
done

echo "---------------------------------------------"
for s in $SERVICES; do
  st=$(systemctl is-active "schoolhelp-$s" || true)
  printf "  schoolhelp-%-8s : %s\n" "$s" "$st"
done
echo "---------------------------------------------"
log "监听端口："
ss -ltnp 2>/dev/null | grep -E ':8080|:8101|:8102|:8103' || err "端口未全部监听，查看日志：journalctl -u schoolhelp-gateway -n 100"

# 冒烟：网关登录接口（连不上不算失败，仅提示）
log "冒烟测试（网关 8080 登录接口）..."
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST http://127.0.0.1:8080/api/user/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' || echo 000)
echo "  /api/user/auth/login HTTP $code （200=网关+user+DB全通；4xx=账号问题；000/5xx=后端未就绪）"
ok "重启流程结束"
