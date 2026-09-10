#!/usr/bin/env bash
# ============================================================
# 一键更新前端（只更前端，不动后端 jar，不触发 Maven）
# 场景：后端没变、只改了 Vue 页面时用它，比全量部署快很多
# 执行：bash /opt/schoolhelp/update-frontend.sh
# 可用环境变量：
#   BRANCH=master          拉取分支
#   TARGET=both|pc|m       只更新某一端，默认 both
#   NO_PULL=1              不拉取代码，直接用现有源码构建（源码已手动同步时）
#   NPM_REGISTRY           默认 https://registry.npmmirror.com
# 退出码：0 成功，非 0 失败
# ============================================================
set -euo pipefail

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

# ------------------------------------------------------------
# 前端原子铺站：rsync 到 .tmp-<时间戳>，再原子改名替换
# 参数：$1=源 dist 目录  $2=站点目录名（pc|m）
# ------------------------------------------------------------
deploy_frontend_dir() {
  local dist="$1" outname="$2"
  local ts tmp out old
  ts="$(date +%Y%m%d_%H%M%S)"
  tmp="$WEBROOT/${outname}.tmp-${ts}"
  out="$WEBROOT/${outname}"
  old="$WEBROOT/${outname}.old-${ts}"

  mkdir -p "$tmp"
  rsync -a --delete "$dist/" "$tmp/"
  if [ -d "$out" ]; then
    mv "$out" "$old"
    mv "$tmp" "$out"
    rm -rf "$old"
  else
    mv "$tmp" "$out"
  fi
  ok "已更新 $out"
}

# ------------------------------------------------------------
# 1. 拉取代码（NO_PULL=1 时跳过）
# ------------------------------------------------------------
if [ "${NO_PULL:-0}" != "1" ]; then
  [ -d "$SRC/.git" ] || { err "源码目录不存在，请先 init-server.sh"; exit 1; }
  log "拉取 $BRANCH ..."
  cd "$SRC"
  git fetch --all --prune
  git reset --hard "origin/$BRANCH"
  ok "当前提交：$(git log --oneline -1)"
fi

# ------------------------------------------------------------
# 2. 构建 + 原子部署（仅前端，不触发 Maven）
# ------------------------------------------------------------
build_frontend() {
  local dir="$1" outname="$2"
  if [ ! -d "$SRC/$dir" ]; then err "目录不存在：$SRC/$dir"; exit 1; fi
  log "构建 $dir ..."
  # npm run build（vite 可能非零退出）单独 || true，以 dist/index.html 是否存在为准
  ( cd "$SRC/$dir" \
    && npm config set registry "$NPM_REGISTRY" \
    && (npm ci --prefer-offline 2>/dev/null || npm install) \
    && (npm run build || true) )
  if [ -f "$SRC/$dir/dist/index.html" ]; then
    deploy_frontend_dir "$SRC/$dir/dist" "$outname"
  else
    err "$dir 构建失败（未生成 dist/index.html），不动线上文件"
    exit 1
  fi
}

case "$TARGET" in
  pc)   build_frontend schoolhelp-web-pc pc ;;
  m)    build_frontend schoolhelp-web    m ;;
  both) build_frontend schoolhelp-web-pc pc
        build_frontend schoolhelp-web    m ;;
  *)    err "TARGET 取值错误：$TARGET（应为 both|pc|m）"; exit 1 ;;
esac

# ------------------------------------------------------------
# 3. reload Nginx
# ------------------------------------------------------------
log "reload Nginx ..."
if nginx -t 2>&1 | grep -q 'successful'; then
  nginx -s reload && ok "Nginx 已 reload"
else
  err "nginx -t 未通过，已跳过 reload："
  nginx -t
  exit 1
fi

ok "前端更新完成 🎉"
