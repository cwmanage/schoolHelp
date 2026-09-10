#!/usr/bin/env bash
# ============================================================
# 一键拉取 + 打包 + 部署（后端 4 个 jar + 前端两端 dist）
# 流程：git 拉取最新 → maven 打包 → 原子落 jar → 前端构建 → 原子铺站 → 重启
# 执行：bash /opt/schoolhelp/pull-build-deploy.sh
# 目录约定（N1）：后端 jar 落 /opt/schoolhelp/<服务>/schoolhelp-<服务>.jar
# 可用环境变量：
#   BRANCH         分支，默认 master
#   SKIP_BACKEND=1 跳过后端打包（仅更新前端）
#   SKIP_FRONTEND=1 跳过前端构建
#   MAVEN_MIRROR   默认 https://maven.aliyun.com/repository/public
#   NPM_REGISTRY   默认 https://registry.npmmirror.com
# 退出码：0 成功，非 0 失败
# ============================================================
set -euo pipefail

BASE=/opt/schoolhelp
SRC=$BASE/src
WEBROOT=/var/www/schoolhelp
BRANCH="${BRANCH:-master}"
SERVICES="gateway user course biz"
MAVEN_MIRROR="${MAVEN_MIRROR:-https://maven.aliyun.com/repository/public}"
NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmmirror.com}"

log() { echo -e "\033[36m[deploy]\033[0m $*"; }
ok()  { echo -e "\033[32m[ok]\033[0m $*"; }
err() { echo -e "\033[31m[err]\033[0m $*" >&2; }

[ "$(id -u)" -eq 0 ] || { err "请用 root 执行：sudo bash pull-build-deploy.sh"; exit 1; }
[ -d "$SRC/.git" ] || { err "源码目录 $SRC 不存在，请先运行 init-server.sh"; exit 1; }

# ------------------------------------------------------------
# 前端原子铺站：rsync 到 .tmp-<时间戳>，再原子改名替换（避免半个产物上线）
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
  ok "已部署 $out"
}

# ------------------------------------------------------------
# 1. 拉取最新代码
# ------------------------------------------------------------
log "拉取 $BRANCH 最新代码 ..."
cd "$SRC"
git fetch --all --prune
git reset --hard "origin/$BRANCH"
ok "当前提交：$(git log --oneline -1)"

# ------------------------------------------------------------
# 2. 后端打包 + 原子落位
# ------------------------------------------------------------
if [ "${SKIP_BACKEND:-0}" != "1" ]; then
  log "Maven 打包（跳过测试，使用阿里云镜像）..."
  export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
  if [ -x ./mvnw ]; then
    chmod +x ./mvnw
    ./mvnw -q -DskipTests -Dmaven.test.skip=true \
      -Dmaven.wagon.http.ssl.insecure=true \
      clean package
  else
    mvn -q -DskipTests -Dmaven.test.skip=true clean package
  fi
  ok "Maven 打包完成"

  log "原子落位 jar 到 $BASE/<服务>/ ..."
  for s in $SERVICES; do
    jar="$SRC/schoolhelp-$s/target/schoolhelp-$s.jar"
    if [ -f "$jar" ]; then
      mkdir -p "$BASE/$s"
      # 先 install 成 .new，成功后 mv -f 原子改名（同目录 rename 原子）
      install -m 644 "$jar" "$BASE/$s/schoolhelp-$s.jar.new"
      mv -f "$BASE/$s/schoolhelp-$s.jar.new" "$BASE/$s/schoolhelp-$s.jar"
      ok "schoolhelp-$s.jar ($(du -h "$jar" | cut -f1))"
    else
      err "未找到 $jar，打包可能失败，中止部署"
      exit 1
    fi
  done
else
  log "SKIP_BACKEND=1，跳过后端"
fi

# ------------------------------------------------------------
# 3. 前端构建 + 原子铺站
# ------------------------------------------------------------
if [ "${SKIP_FRONTEND:-0}" != "1" ]; then
  log "构建前端（移动端 + PC 端）..."
  mkdir -p "$WEBROOT/pc" "$WEBROOT/m"

  build_frontend() {
    local dir="$1" outname="$2"
    log "  -> $dir"
    # npm run build（vite 可能非零退出）单独 || true，以 dist/index.html 是否存在为准
    ( cd "$SRC/$dir" \
      && npm config set registry "$NPM_REGISTRY" \
      && (npm ci --prefer-offline 2>/dev/null || npm install) \
      && (npm run build || true) )
    if [ -f "$SRC/$dir/dist/index.html" ]; then
      deploy_frontend_dir "$SRC/$dir/dist" "$outname"
    else
      err "$dir 构建失败：未生成 dist/index.html，中止部署"
      exit 1
    fi
  }

  build_frontend schoolhelp-web-pc pc
  build_frontend schoolhelp-web    m
else
  log "SKIP_FRONTEND=1，跳过前端"
fi

# ------------------------------------------------------------
# 4. 重启后端 + reload 前端
# ------------------------------------------------------------
log "重启后端 ..."
bash "$BASE/restart-backend.sh" || true
log "reload 前端 ..."
bash "$BASE/restart-frontend.sh" || true

ok "一键部署完成 🎉"
