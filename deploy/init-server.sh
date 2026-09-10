#!/usr/bin/env bash
# ============================================================
# schoolHelp 服务器一次性初始化脚本（Ubuntu/Debian）
# 用途：安装依赖(git/maven/rsync)、建目录、写运行环境、装 systemd、放置运维脚本
# 执行：sudo bash init-server.sh
# 幂等：可重复执行，不会覆盖已存在的 .env / 已部署内容
# ============================================================
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/cwmanage/schoolHelp.git}"
BASE=/opt/schoolhelp
APP=$BASE/app
SRC=$BASE/src
WEBROOT=/var/www/schoolhelp
BRANCH="${BRANCH:-master}"
SERVICES="gateway user course biz"

log() { echo -e "\033[36m[init]\033[0m $*"; }
ok()  { echo -e "\033[32m[ok]\033[0m $*"; }
err() { echo -e "\033[31m[err]\033[0m $*" >&2; }

[ "$(id -u)" -eq 0 ] || { err "请用 root 执行：sudo bash init-server.sh"; exit 1; }

# ---------- 1. 安装系统依赖 ----------
log "安装 git / maven / rsync ..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y git maven rsync curl ca-certificates

# ---------- 2. 检查 JDK17 ----------
if ! command -v java >/dev/null 2>&1; then
  log "未检测到 java，尝试安装 openjdk-17-jdk ..."
  apt-get install -y openjdk-17-jdk
fi
JAVA_BIN="$(readlink -f "$(command -v java)")"
JAVA_HOME_D="$(dirname "$(dirname "$JAVA_BIN")")"
ok "JDK: $JAVA_BIN (JAVA_HOME=$JAVA_HOME_D)"
java -version 2>&1 | head -1

# ---------- 3. 检查 Node 18+ ----------
if ! command -v node >/dev/null 2>&1; then
  log "未检测到 node，使用 NodeSource 安装 Node 18 ..."
  curl -fsSL https://deb.nodesource.com/setup_18.x | bash -
  apt-get install -y nodejs
fi
ok "Node: $(node -v), npm: $(npm -v)"

# ---------- 4. 建目录 ----------
log "创建目录 ..."
mkdir -p "$APP/logs" "$SRC" "$WEBROOT/pc" "$WEBROOT/m"

# ---------- 5. 写运行环境文件（含解密口令，权限 600） ----------
if [ ! -f "$BASE/.env" ]; then
  if [ -z "${JASYPT_ENCRYPTOR_PASSWORD:-}" ]; then
    err "缺少 JASYPT_ENCRYPTOR_PASSWORD。请这样执行："
    err "  JASYPT_ENCRYPTOR_PASSWORD='你的解密口令' sudo -E bash init-server.sh"
    err "（该口令用于解密 application.yml 里的 ENC(...) 数据库/Nacos 密码）"
    exit 1
  fi
  printf 'JASYPT_ENCRYPTOR_PASSWORD=%s\n' "$JASYPT_ENCRYPTOR_PASSWORD" > "$BASE/.env"
  chmod 600 "$BASE/.env"
  ok "已写入 $BASE/.env（权限 600）"
else
  ok "$BASE/.env 已存在，跳过"
fi

# ---------- 6. 安装 systemd 单元（4 个后端服务） ----------
log "安装 systemd 单元 ..."
for s in $SERVICES; do
  cat > "/etc/systemd/system/schoolhelp-$s.service" <<EOF
[Unit]
Description=schoolHelp $s service
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=$APP
EnvironmentFile=$BASE/.env
ExecStart=$JAVA_BIN -Xms128m -Xmx512m -jar $APP/schoolhelp-$s.jar
Restart=on-failure
RestartSec=5
StandardOutput=append:$APP/logs/$s.log
StandardError=append:$APP/logs/$s.log

[Install]
WantedBy=multi-user.target
EOF
  ok "已安装 schoolhelp-$s.service"
done
systemctl daemon-reload
for s in $SERVICES; do systemctl enable "schoolhelp-$s" >/dev/null 2>&1 || true; done

# ---------- 7. 放置运维脚本 ----------
log "放置运维脚本 ..."
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
for f in restart-backend.sh restart-frontend.sh pull-build-deploy.sh update-frontend.sh; do
  if [ -f "$SCRIPT_DIR/$f" ]; then
    install -m 755 "$SCRIPT_DIR/$f" "$BASE/$f"
    ok "$BASE/$f"
  fi
done

# ---------- 8. 初始化源码目录（可选） ----------
if [ ! -d "$SRC/.git" ]; then
  log "克隆仓库到 $SRC ..."
  git clone -b "$BRANCH" "$REPO_URL" "$SRC" || err "克隆失败（若是私有仓库，请先配置 git 凭据/Token）"
fi

ok "初始化完成。下一步："
echo "  1) 确认 Nginx 站点已配置（PC:80 / 移动:8081）"
echo "  2) 首次部署：bash $BASE/pull-build-deploy.sh"
