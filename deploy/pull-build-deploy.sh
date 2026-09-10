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
#   BASE / WEBROOT 安装根 / 站点根（默认 /opt/schoolhelp、/var/www/schoolhelp）
# 退出码：0 成功，非 0 失败
# ============================================================
set -euo pipefail

BASE="${BASE:-/opt/schoolhelp}"
SRC=$BASE/src
WEBROOT="${WEBROOT:-/var/www/schoolhelp}"
BRANCH="${BRANCH:-master}"
SERVICES="gateway user course biz"
MAVEN_MIRROR="${MAVEN_MIRROR:-https://maven.aliyun.com/repository/public}"
NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmmirror.com}"

log() { echo -e "\033[36m[deploy]\033[0m $*"; }
ok()  { echo -e "\033[32m[ok]\033[0m $*"; }
err() { echo -e "\033[31m[err]\033[0m $*" >&2; }

# ------------------------------------------------------------
# 构建前 Node 版本守卫：vite 8 / rolldown 1.x 需 ^20.19.0 || >=22.12.0
#   （Node 20.12+ 才有 util.styleText；否则 vite build 报
#     "does not provide an export named 'styleText'"）
#   判定边界：20.x 且 minor>=19 满足；21.x 不满足；22.x 需 minor>=12；>=23 满足
# ------------------------------------------------------------
node_version_satisfies() {
  local v="${1#v}" major minor
  # 仅剥离尾随空白（兼容 Windows 下 `node -v` 可能带的 \r）；前导空白不剥离 → 保守 FAIL
  v="${v%"${v##*[![:space:]]}"}"
  # 剥离 v 前缀后必须严格形如 X.Y 或 X.Y.Z；其余（如 v22 / 22.12.0-nightly / 含空格 / 空串）一律 FAIL
  if [[ ! "$v" =~ ^[0-9]+\.[0-9]+(\.[0-9]+)?$ ]]; then return 1; fi
  major="${v%%.*}"; minor="${v#*.}"; minor="${minor%%.*}"
  if [ "$major" -eq 20 ] && [ "$minor" -ge 19 ]; then return 0; fi   # ^20.19.0
  if [ "$major" -gt 22 ]; then return 0; fi                          # >=23
  if [ "$major" -eq 22 ] && [ "$minor" -ge 12 ]; then return 0; fi   # >=22.12.0
  return 1
}
require_node_version() {
  local have
  have="$(node -v 2>/dev/null || true)"
  if [ -z "$have" ]; then
    err "未检测到 node，无法构建前端。需要 Node 22 LTS（^20.19.0 || >=22.12.0）"
    err "升级：curl -fsSL https://deb.nodesource.com/setup_22.x | bash - && apt-get install -y nodejs"
    exit 1
  fi
  if ! node_version_satisfies "$have"; then
    err "Node 版本不满足要求：当前 $have，需要 ^20.19.0 || >=22.12.0"
    err "（原因：vite 8 / rolldown 1.x 依赖 Node 20.12+ 的 util.styleText）"
    err "升级：curl -fsSL https://deb.nodesource.com/setup_22.x | bash - && apt-get install -y nodejs"
    exit 1
  fi
}

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
    if ! mv "$tmp" "$out"; then
      # 第二个 mv 失败：立刻把旧目录还原，避免站点缺失（纵深防御；同文件系统 rename 极少失败）
      mv "$old" "$out" 2>/dev/null || true
      err "站点替换失败，已还原旧目录：$out"
      return 1
    fi
    rm -rf "$old" 2>/dev/null || err "清理旧目录失败（不影响新站）：$old"
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
#    构建失败 → exit 1，绝不触碰线上站点目录（避免半成品 dist 上线）
# ------------------------------------------------------------
if [ "${SKIP_FRONTEND:-0}" != "1" ]; then
  log "构建前端（移动端 + PC 端）..."
  # 守卫必须是本脚本任何 mkdir/rm/mv 之前的第一步：
  # Node 不合格时对 $WEBROOT 零写入（不创建 pc/m），$SRC 的 dist 也不删
  require_node_version
  mkdir -p "$WEBROOT/pc" "$WEBROOT/m"

  build_frontend() {
    local dir="$1" outname="$2" rc=0
    require_node_version
    if [ ! -d "$SRC/$dir" ]; then err "目录不存在：$SRC/$dir"; exit 1; fi
    log "  -> $dir"
    # 先清空 dist，杜绝上一轮残留产物混淆「构建成功」判定
    rm -rf "$SRC/$dir/dist"
    # 用真实退出码判定（vite 脚手架偶发非零 → 重试一次再判，不无条件吞掉退出码）
    ( cd "$SRC/$dir" \
      && npm config set registry "$NPM_REGISTRY" \
      && (npm ci --prefer-offline 2>/dev/null || npm install) \
      && (npm run build || npm run build) ) || rc=$?
    if [ "$rc" -ne 0 ]; then
      err "$dir 构建失败（退出码 $rc），不触碰线上站点目录，中止部署"
      exit 1
    fi
    # 二次断言：产物必须存在
    if [ ! -f "$SRC/$dir/dist/index.html" ]; then
      err "$dir 构建异常（退出码 0 但未生成 dist/index.html），不触碰线上站点目录，中止部署"
      exit 1
    fi
    deploy_frontend_dir "$SRC/$dir/dist" "$outname"
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
