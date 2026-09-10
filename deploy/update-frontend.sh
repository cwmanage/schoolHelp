#!/usr/bin/env bash
# ============================================================
# 一键更新前端（只更前端，不动后端 jar）
# 场景：后端没变、只改了 Vue 页面时用它，比全量部署快很多
# 执行：bash /opt/schoolhelp/update-frontend.sh
# 可用环境变量：
#   BRANCH=master          拉取分支
#   TARGET=both|pc|m       只更新某一端，默认 both
#   NO_PULL=1              不拉取代码，直接用现有源码构建（源码已手动同步时）
# ============================================================
set -uo pipefail

BASE=/opt/schoolhelp
SRC=$BASE/src
WEBROOT=/var/www/schoolhelp
BRANCH="${BRANCH:-master}"
TARGET="${TARGET:-both}"
NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmmirror.com}"

log() { echo -e "\033[36m[frontend]\033[0m $*"; }
ok()  { echo -e "\033[32m[ok]\033[0m $*"; }
err() { echo -e "\033[31m[err]\033[0m $*" >&2; }

[ "$(id -u)" -eq 0 ] || { err "请用 root 执行：sudo bash update-frontend.sh"; exit 1; }

# ---------- 1. 拉取代码 ----------
if [ "${NO_PULL:-0}" != "1" ]; then
  [ -d "$SRC/.git" ] || { err "源码目录不存在，请先 init-server.sh"; exit 1; }
  log "拉取 $BRANCH ..."
  cd "$SRC"
  git fetch --all --prune
  git reset --hard "origin/$BRANCH"
  ok "当前提交：$(git log --oneline -1)"
fi

# ---------- 2. 构建 ----------
build_frontend() {
  local dir="$1" out="$2"
  if [ ! -d "$SRC/$dir" ]; then err "目录不存在：$SRC/$dir"; return 1; fi
  log "构建 $dir ..."
  ( cd "$SRC/$dir" \
    && npm config set registry "$NPM_REGISTRY" \
    && (npm ci --prefer-offline 2>/dev/null || npm install) \
    && npm run build )
  if [ -f "$SRC/$dir/dist/index.html" ]; then
    mkdir -p "$out"
    rsync -a --delete "$SRC/$dir/dist/" "$out/"
    ok "已更新 $out"
  else
    err "$dir 构建失败（未生成 dist/index.html）"
    return 1
  fi
}

case "$TARGET" in
  pc)   build_frontend schoolhelp-web-pc "$WEBROOT/pc" ;;
  m)    build_frontend schoolhelp-web    "$WEBROOT/m" ;;
  both) build_frontend schoolhelp-web-pc "$WEBROOT/pc"
        build_frontend schoolhelp-web    "$WEBROOT/m" ;;
  *)    err "TARGET 取值错误：$TARGET（应为 both|pc|m）"; exit 1 ;;
esac

# ---------- 3. reload Nginx ----------
log "reload Nginx ..."
nginx -t && nginx -s reload && ok "Nginx 已 reload"

ok "前端更新完成 🎉"
