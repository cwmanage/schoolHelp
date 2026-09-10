#!/usr/bin/env bash
# ============================================================
# schoolHelp 服务器一次性初始化脚本（Ubuntu/Debian）
# 用途：装依赖(git/maven/rsync/node)、停旧裸进程、备份旧文件、
#       逐服务建目录、写 .env、渲染 systemd 具名单元、放置运维脚本、
#       nginx 前置检查与站点配置同步、克隆仓库
# 执行：JASYPT_ENCRYPTOR_PASSWORD='<40位口令>' sudo -E bash init-server.sh
# 幂等：可重复执行；.env 已存在且口令一致则跳过（不覆盖）
# 口令轮换：JASYPT_ENCRYPTOR_PASSWORD='<新口令>' FORCE_ENV=1 bash init-server.sh
#           （先把旧 .env 备份到 _backup_<时间戳>/.env.bak，再用新口令覆盖）
#
# 环境变量：
#   JASYPT_ENCRYPTOR_PASSWORD  必填，解密 application.yml 里 ENC(...) 的口令
#   FORCE_ENV=1                .env 已存在且口令不一致时，备份旧文件后强制覆盖
#   REPO_URL / BRANCH          源码仓库地址 / 分支（默认 master）
#   BASE / WEBROOT             安装根 / 站点根（默认 /opt/schoolhelp、/var/www/schoolhelp）
#
# 目录约定（N1）：BASE=/opt/schoolhelp，jar/logs/uploads 均下沉到 $BASE/<服务>/
# ============================================================
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/cwmanage/schoolHelp.git}"
BASE="${BASE:-/opt/schoolhelp}"
SRC=$BASE/src
WEBROOT="${WEBROOT:-/var/www/schoolhelp}"
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
# 已由 systemd 托管（cgroup 含 schoolhelp-<svc>.service）的进程会跳过，避免误杀生产服务
stop_bare_processes() {
  local port pid cmd pids released cgroup
  log "检查并停止占用 $PORTS 的裸 java 进程 ..."
  for port in $PORTS; do
    pids="$(ss -ltnp 2>/dev/null | grep ":$port " | grep -oE 'pid=[0-9]+' | cut -d= -f2 | sort -u || true)"
    if [ -z "$pids" ]; then continue; fi
    for pid in $pids; do
      cmd="$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)"
      if ! echo "$cmd" | grep -q 'schoolhelp-.*\.jar'; then
        err "  端口 $port 被非本项目进程占用（PID $pid），请手工处理：$cmd"
        continue
      fi
      # 已由 systemd 托管 → 交给 systemctl，不 kill（重跑本脚本不应打断生产服务）
      # 读不到 cgroup 时（权限/竞态）按“裸进程”处理，避免漏停导致端口冲突
      cgroup="$(cat "/proc/$pid/cgroup" 2>/dev/null || true)"
      if [ -n "$cgroup" ] && echo "$cgroup" | grep -qE 'schoolhelp-[a-z]+\.service'; then
        ok "  端口 $port / PID $pid 由 systemd 托管，跳过（请用 systemctl 管理）"
        continue
      fi
      log "  停止裸进程：端口 $port / PID $pid / $cmd"
      kill "$pid" 2>/dev/null || true
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

# nginx 前置检查与站点配置同步（本项目用宝塔面板的 nginx，勿再 apt 安装以免抢 80）
# 说明：本函数所有失败均不 exit（非致命），以免中断 systemd 单元 / 运维脚本等已完成的初始化；
#       nginx 站点问题会由 smoke-verify.sh 在验收阶段暴露。
setup_nginx() {
  local bt_nginx="/www/server/nginx/sbin/nginx"
  local bt_vhost="/www/server/panel/vhost/nginx"
  local conf_src="$SCRIPT_DIR/nginx-schoolhelp.conf"

  # 1) 前置检查：nginx 必须已安装（人工前置依赖）
  if command -v nginx >/dev/null 2>&1; then
    ok "nginx 可用：$(command -v nginx)"
  elif [ -x "$bt_nginx" ]; then
    ok "nginx 可用（宝塔）：$bt_nginx"
  else
    err "未检测到 nginx。nginx 为前置人工依赖，请先装好。"
    err "本项目使用宝塔面板的 nginx，站点配置目录：$bt_vhost"
    err "（已跳过 nginx 站点配置同步，不影响其它初始化步骤）"
    return 0
  fi

  # 2) 若模板可用且宝塔 vhost 目录存在 → 备份旧配置后同步，nginx -t 校验，失败则回滚
  if [ ! -f "$conf_src" ]; then
    err "未找到站点配置模板 $conf_src，跳过同步"
    return 0
  fi
  if [ ! -d "$bt_vhost" ]; then
    err "宝塔 vhost 目录不存在（$bt_vhost），跳过站点配置同步"
    return 0
  fi

  local dst="$bt_vhost/schoolhelp.conf"
  local bak="$BACKUP_DIR/nginx-schoolhelp.conf.bak"
  if [ -f "$dst" ]; then
    mkdir -p "$BACKUP_DIR"
    cp -p "$dst" "$bak"
    log "  已备份旧站点配置 → $bak"
  fi
  install -m 644 "$conf_src" "$dst"
  if nginx -t >/dev/null 2>&1; then
    ok "站点配置已同步并校验通过：$dst"
    if nginx -s reload >/dev/null 2>&1; then
      ok "nginx 已 reload"
    else
      err "nginx reload 失败，请手工检查（配置已就位）"
    fi
  else
    err "nginx -t 未通过，回滚站点配置："
    nginx -t
    if [ -f "$bak" ]; then
      cp -p "$bak" "$dst"
      if nginx -t >/dev/null 2>&1; then ok "已回滚到旧配置"; else err "回滚后仍校验失败，请手工处理"; fi
    else
      rm -f "$dst"
      err "原无旧配置，已移除新配置（$dst）"
    fi
  fi
  return 0
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
# 7. 写运行环境文件（含解密口令，权限 600）
#    fail-fast：.env 已存在且口令与本次不一致时默认中止（口令轮换场景），
#    需显式 FORCE_ENV=1 才覆盖，且覆盖前先备份旧 .env
# ------------------------------------------------------------
if [ -z "${JASYPT_ENCRYPTOR_PASSWORD:-}" ]; then
  err "缺少 JASYPT_ENCRYPTOR_PASSWORD。请这样执行："
  err "  JASYPT_ENCRYPTOR_PASSWORD='你的40位口令' sudo -E bash init-server.sh"
  err "  （口令轮换：JASYPT_ENCRYPTOR_PASSWORD='<新口令>' FORCE_ENV=1 bash init-server.sh）"
  err "（该口令用于解密 application.yml 里的 ENC(...) 数据库/Nacos 密码）"
  exit 1
fi

if [ ! -f "$BASE/.env" ]; then
  printf 'JASYPT_ENCRYPTOR_PASSWORD=%s\n' "$JASYPT_ENCRYPTOR_PASSWORD" > "$BASE/.env"
  chmod 600 "$BASE/.env"
  ok "已写入 $BASE/.env（权限 600）"
else
  cur="$(sed -n 's/^JASYPT_ENCRYPTOR_PASSWORD=//p' "$BASE/.env" | head -1 | tr -d '\r\n')"
  if [ "$cur" = "$JASYPT_ENCRYPTOR_PASSWORD" ]; then
    ok "$BASE/.env 已存在且口令一致，跳过"
  elif [ "${FORCE_ENV:-0}" != "1" ]; then
    err "检测到 $BASE/.env 已存在且口令与本次不一致。这通常是口令轮换场景，已中止以免用旧口令解新密文导致 4 服务全部起不来。"
    err "若确认要轮换，请确认后这样执行："
    err "  JASYPT_ENCRYPTOR_PASSWORD='<新口令>' FORCE_ENV=1 bash init-server.sh"
    err "（会先把旧 .env 备份到 $BASE/_backup_<时间戳>/.env.bak 再覆盖）"
    exit 1
  else
    mkdir -p "$BACKUP_DIR"
    cp -p "$BASE/.env" "$BACKUP_DIR/.env.bak"
    printf 'JASYPT_ENCRYPTOR_PASSWORD=%s\n' "$JASYPT_ENCRYPTOR_PASSWORD" > "$BASE/.env"
    chmod 600 "$BASE/.env"
    ok "口令已轮换：旧 .env 已备份到 $BACKUP_DIR/.env.bak，新口令已写入 $BASE/.env（权限 600）"
  fi
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
# 10. nginx 前置检查与站点配置同步（宝塔面板，非致命）
# ------------------------------------------------------------
log "检查 nginx 并同步站点配置 ..."
setup_nginx

# ------------------------------------------------------------
# 11. 初始化源码目录（可选）
# ------------------------------------------------------------
if [ ! -d "$SRC/.git" ]; then
  log "克隆仓库到 $SRC ..."
  git clone -b "$BRANCH" "$REPO_URL" "$SRC" || err "克隆失败（若是私有仓库，请先配置 git 凭据/Token）"
fi

ok "初始化完成。下一步："
echo "  1) 确认 Nginx 站点已配置（PC:80 / 移动:8081；宝塔 vhost：/www/server/panel/vhost/nginx/schoolhelp.conf）"
echo "  2) 首次部署：bash $BASE/pull-build-deploy.sh"
echo "  3) 验收自检：bash $BASE/smoke-verify.sh"
