#!/usr/bin/env bash
# ============================================================
# schoolHelp 服务器一次性初始化脚本（Ubuntu/Debian）
# 用途：装依赖(git/maven/rsync/node)、停旧裸进程、备份旧文件、
#       逐服务建目录、写 .env、渲染 systemd 具名单元、放置运维脚本、克隆仓库
# 执行：JASYPT_ENCRYPTOR_PASSWORD='<40位口令>' sudo -E bash init-server.sh
# 幂等：可重复执行，不覆盖已存在的 .env
#
# 目录约定（N1）：BASE=/opt/schoolhelp，jar/logs/uploads 均下沉到 $BASE/<服务>/
# ============================================================
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/cwmanage/schoolHelp.git}"
BASE="${BASE:-/opt/schoolhelp}"
SRC=$BASE/src
WEBROOT=/var/www/schoolhelp
BRANCH="${BRANCH:-master}"
SERVICES="gateway user course biz"
PORTS="8080 8101 8102 8103"
BACKUP_DIR="$BASE/_backup_$(date +%Y%m%d_%H%M)"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TMPL="$SCRIPT_DIR/systemd/schoolhelp.service.tmpl"

log() { echo -e "\033[36m[init]\033[0m $*"; }
ok()  { echo -e "\033[32m[ok]\033[0m $*"; }
err() { echo -e "\033[31m[err]\033[0m $*" >&2; }

[ "$(id -u)" -eq 0 ] || { err "请用 root 执行：sudo bash init-server.sh"; exit 1; }

# ------------------------------------------------------------
# 辅助函数
# ------------------------------------------------------------
# 按端口找出旧裸 java 进程（本项目 schoolhelp-*.jar）并停止，等待端口真正释放
stop_bare_processes() {
  local port pid cmd pids released
  log "检查并停止占用 $PORTS 的裸 java 进程 ..."
  for port in $PORTS; do
    pids="$(ss -ltnp 2>/dev/null | grep ":$port " | grep -oE 'pid=[0-9]+' | cut -d= -f2 | sort -u || true)"
    if [ -z "$pids" ]; then continue; fi
    for pid in $pids; do
      cmd="$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)"
      if echo "$cmd" | grep -q 'schoolhelp-.*\.jar'; then
        log "  停止裸进程：端口 $port / PID $pid / $cmd"
        kill "$pid" 2>/dev/null || true
      else
        err "  端口 $port 被非本项目进程占用（PID $pid），请手工处理：$cmd"
      fi
    done
  done

  # 等待端口释放（最多 30s/端口），否则后面 systemctl start 会撞 Address already in use
  for port in $PORTS; do
    released=0
    for _ in $(seq 1 30); do
      if ss -ltn 2>/dev/null | grep -q ":$port "; then
        sleep 1
      else
        released=1
        break
      fi
    done
    if [ "$released" -eq 1 ]; then
      ok "  端口 $port 已释放"
    else
      err "  端口 $port 仍被占用，后续启动可能失败（请检查后重跑本脚本）"
    fi
  done
}

# 备份并移除根目录旧 jar 与旧 *.log（Q2：先备份后移除）
backup_legacy_files() {
  local f found=0
  for f in "$BASE"/schoolhelp-*.jar "$BASE"/gw.log "$BASE"/user.log "$BASE"/course.log "$BASE"/biz.log; do
    if [ -e "$f" ]; then found=1; break; fi
  done
  if [ "$found" -eq 0 ]; then
    ok "根目录无旧 jar / 旧 log，跳过备份"
    return 0
  fi

  mkdir -p "$BACKUP_DIR"
  for f in "$BASE"/schoolhelp-*.jar; do
    [ -e "$f" ] || continue
    mv -f "$f" "$BACKUP_DIR/"
    ok "  备份旧 jar：$(basename "$f")"
  done
  for f in "$BASE"/gw.log "$BASE"/user.log "$BASE"/course.log "$BASE"/biz.log; do
    [ -e "$f" ] || continue
    mv -f "$f" "$BACKUP_DIR/"
    ok "  备份旧 log：$(basename "$f")"
  done
  ok "旧文件已备份至 $BACKUP_DIR（确认新部署稳定后可自行删除）"
}

# ------------------------------------------------------------
# 1. 安装系统依赖
# ------------------------------------------------------------
log "安装 git / maven / rsync ..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y git maven rsync curl ca-certificates

# ------------------------------------------------------------
# 2. 检查 JDK17
# ------------------------------------------------------------
if ! command -v java >/dev/null 2>&1; then
  log "未检测到 java，尝试安装 openjdk-17-jdk ..."
  apt-get install -y openjdk-17-jdk
fi
JAVA_BIN="$(readlink -f "$(command -v java)")"
JAVA_HOME_D="$(dirname "$(dirname "$JAVA_BIN")")"
ok "JDK: $JAVA_BIN (JAVA_HOME=$JAVA_HOME_D)"
java -version 2>&1 | head -1

# ------------------------------------------------------------
# 3. 检查 Node 18+
# ------------------------------------------------------------
if ! command -v node >/dev/null 2>&1; then
  log "未检测到 node，使用 NodeSource 安装 Node 18 ..."
  curl -fsSL https://deb.nodesource.com/setup_18.x | bash -
  apt-get install -y nodejs
fi
ok "Node: $(node -v), npm: $(npm -v)"

# ------------------------------------------------------------
# 4. 停止旧裸 java 进程（迁移关键步骤，避免端口冲突）
# ------------------------------------------------------------
stop_bare_processes

# ------------------------------------------------------------
# 5. 备份并移除根目录旧 jar / 旧 log
# ------------------------------------------------------------
log "备份并清理根目录旧文件 ..."
backup_legacy_files

# ------------------------------------------------------------
# 6. 逐服务建目录（N1：无 app 中间层）
# ------------------------------------------------------------
log "创建目录 ..."
mkdir -p "$SRC" "$WEBROOT/pc" "$WEBROOT/m"
for s in $SERVICES; do
  mkdir -p "$BASE/$s" "$BASE/$s/logs" "$BASE/$s/uploads"
  ok "  $BASE/$s/{logs,uploads}"
done

# ------------------------------------------------------------
# 7. 写运行环境文件（含解密口令，权限 600；已存在则跳过）
# ------------------------------------------------------------
if [ ! -f "$BASE/.env" ]; then
  if [ -z "${JASYPT_ENCRYPTOR_PASSWORD:-}" ]; then
    err "缺少 JASYPT_ENCRYPTOR_PASSWORD。请这样执行："
    err "  JASYPT_ENCRYPTOR_PASSWORD='你的40位口令' sudo -E bash init-server.sh"
    err "（该口令用于解密 application.yml 里的 ENC(...) 数据库/Nacos 密码）"
    exit 1
  fi
  printf 'JASYPT_ENCRYPTOR_PASSWORD=%s\n' "$JASYPT_ENCRYPTOR_PASSWORD" > "$BASE/.env"
  chmod 600 "$BASE/.env"
  ok "已写入 $BASE/.env（权限 600）"
else
  ok "$BASE/.env 已存在，跳过"
fi

# ------------------------------------------------------------
# 8. 安装 systemd 具名单元（模板 + sed 渲染，共 4 个）
# ------------------------------------------------------------
log "安装 systemd 单元 ..."
if [ ! -f "$TMPL" ]; then
  err "未找到 systemd 模板：$TMPL（请确认 deploy/systemd/ 已随脚本一起上传）"
  exit 1
fi
for s in $SERVICES; do
  sed -e "s|__SVC__|$s|g" \
      -e "s|__JAVA_BIN__|$JAVA_BIN|g" \
      -e "s|__BASE__|$BASE|g" \
      "$TMPL" > "/etc/systemd/system/schoolhelp-$s.service"
  ok "已安装 schoolhelp-$s.service"
done
systemctl daemon-reload
for s in $SERVICES; do
  systemctl enable "schoolhelp-$s" >/dev/null 2>&1 || true
  ok "  schoolhelp-$s 已 enable（开机自启）"
done
log "提示：此处仅 enable，不 --now 启动；首次 jar 由 pull-build-deploy.sh 生成后再启动"

# ------------------------------------------------------------
# 9. 放置运维脚本（755）
# ------------------------------------------------------------
log "放置运维脚本 ..."
for f in restart-backend.sh restart-frontend.sh pull-build-deploy.sh update-frontend.sh smoke-verify.sh; do
  if [ -f "$SCRIPT_DIR/$f" ]; then
    install -m 755 "$SCRIPT_DIR/$f" "$BASE/$f"
    ok "$BASE/$f"
  fi
done

# ------------------------------------------------------------
# 10. 初始化源码目录（可选）
# ------------------------------------------------------------
if [ ! -d "$SRC/.git" ]; then
  log "克隆仓库到 $SRC ..."
  git clone -b "$BRANCH" "$REPO_URL" "$SRC" || err "克隆失败（若是私有仓库，请先配置 git 凭据/Token）"
fi

ok "初始化完成。下一步："
echo "  1) 确认 Nginx 站点已配置（PC:80 / 移动:8081）"
echo "  2) 首次部署：bash $BASE/pull-build-deploy.sh"
echo "  3) 验收自检：bash $BASE/smoke-verify.sh"
