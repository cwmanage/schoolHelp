#!/usr/bin/env bash
# ============================================================
# 一键重启前端（Nginx；PC 静态站 + 移动端静态站）
# 执行：bash /opt/schoolhelp/restart-frontend.sh
# 说明：前端是纯静态文件，由 Nginx 托管，无需"重启进程"，只需 reload 配置
# ============================================================
set -uo pipefail

WEBROOT="${WEBROOT:-/var/www/schoolhelp}"

log() { echo -e "\033[36m[frontend]\033[0m $*"; }
ok()  { echo -e "\033[32m[ok]\033[0m $*"; }
err() { echo -e "\033[31m[err]\033[0m $*" >&2; }

# 前端目录检查
for d in pc m; do
  if [ ! -f "$WEBROOT/$d/index.html" ]; then
    err "缺少 $WEBROOT/$d/index.html，请先执行 pull-build-deploy.sh 或 update-frontend.sh"
  else
    ok "站点目录存在：$WEBROOT/$d （$(ls "$WEBROOT/$d" | wc -l) 个文件）"
  fi
done

log "测试 Nginx 配置 ..."
if nginx -t 2>&1 | grep -q 'successful'; then
  ok "nginx -t 通过"
  nginx -s reload && ok "Nginx 已 reload"
else
  err "nginx -t 未通过，请修复后再 reload："
  nginx -t
  exit 1
fi

echo "---------------------------------------------"
log "访问验证（服务器本机）："
for p in 80 8081; do
  code=$(curl -s -o /dev/null -w '%{http_code}' --noproxy '*' "http://127.0.0.1:$p/" || echo 000)
  echo "  http://127.0.0.1:$p/  -> HTTP $code"
done
ok "前端重启（reload）完成"
