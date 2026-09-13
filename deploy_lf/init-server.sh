#!/usr/bin/env bash
# ============================================================
# schoolHelp 服务器一次性初始化脚本（Ubuntu/Debian）
# 用途：装依赖(git/maven/rsync/node)、停旧裸进程、备份旧文件、
#       逐服务建目录、写 .env、渲染 systemd 具名单元、放置运维脚本、
#       nginx 前置检查与站点配置同步、克隆仓库
# 执行：JASYPT_ENCRYPTOR_PASSWORD='<40位口令>' sudo -E bash init-server.sh
# 幂等：可重复执行；.env 已存在且口令一致则跳过（不覆盖）
# 口令轮换：JASYPT_ENCRYPTOR_PASSWORD='<新口令>' FORCE_ENV=1 sudo -E bash init-server.sh
#           （先把旧 .env 备份到 _backup_<时间戳>/.env.bak，再用新口令按行覆盖）
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
      # 边界锚定：仅匹配路径段 / 开头的本单元（如 /system.slice/schoolhelp-user.service），
      # 避免把 my-schoolhelp-helper.service 之类误判为本项目托管
      if [ -n "$cgroup" ] && echo "$cgroup" | grep -qE '(^|/)schoolhelp-[a-z]+\.service'; then
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
# 注意：本函数运行在 set -euo pipefail 下，除「判定位」(if/||/&&) 外的任何一条裸命令
#       失败都会终止整个上层脚本 —— 因此下面所有可能失败的外部命令都显式兜底（if / || return 0）。
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

  # 2) 模板与目录前置检查
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
  local rendered="$dst.new-$$"

  # 3) 渲染 WEBROOT + server_name（本机 IP）
  #    - WEBROOT：把模板默认站点根 /var/www/schoolhelp 替换为实际值（默认值下逐字相同）
  #    - server_name：模板已写死真实 IP(47.83.169.101)+`_` 兜底，手工拷贝即可用；
  #      仅当探测到**不同的公网 IP** 时才替换（避免用私网 IP 误覆盖真实公网 IP，也避免模板改 IP 后过期）
  local tmpl_sn_ip det_ip
  tmpl_sn_ip="$(sed -n 's/^[[:space:]]*server_name[[:space:]][[:space:]]*\([^ ;]\+\).*/\1/p' "$conf_src" 2>/dev/null | head -1)"
  det_ip=""
  if command -v curl >/dev/null 2>&1; then
    det_ip="$(curl -s --max-time 3 ifconfig.me 2>/dev/null | tr -d '[:space:]' || true)"
  fi
  if [ -z "$det_ip" ] && command -v hostname >/dev/null 2>&1; then
    det_ip="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
  fi
  # 仅接受公网 IPv4；私网/回环/非法 → 置空，保留模板默认真实 IP
  if ! printf '%s' "$det_ip" | grep -Eq '^([0-9]{1,3}\.){3}[0-9]{1,3}$' \
     || printf '%s' "$det_ip" | grep -Eq '^(10\.|127\.|169\.254\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)'; then
    det_ip=""
  fi

  if [ -z "$tmpl_sn_ip" ]; then
    err "模板 $conf_src 未解析到 server_name，将按原样渲染（请手工确认站点配置里含本机 IP）"
  fi
  # 仅当模板 server_name 是「IPv4」且与实际公网 IP 不同才替换；若模板已被改成域名则保持不动
  if [ -n "$det_ip" ] && [ -n "$tmpl_sn_ip" ] \
     && printf '%s' "$tmpl_sn_ip" | grep -Eq '^([0-9]{1,3}\.){3}[0-9]{1,3}$' \
     && [ "$det_ip" != "$tmpl_sn_ip" ]; then
    log "  本机公网 IP=$det_ip 与模板 server_name IP=$tmpl_sn_ip 不一致 → 按本机 IP 渲染 server_name"
    if ! sed -e "s|/var/www/schoolhelp|${WEBROOT}|g" \
             -e "s|server_name ${tmpl_sn_ip} _;|server_name ${det_ip} _;|g" \
             "$conf_src" > "$rendered" 2>/dev/null; then
      err "渲染站点配置失败（sed），跳过同步"
      rm -f "$rendered" 2>/dev/null || true
      return 0
    fi
  else
    if ! sed "s|/var/www/schoolhelp|${WEBROOT}|g" "$conf_src" > "$rendered" 2>/dev/null; then
      err "渲染站点配置失败（sed），跳过同步"
      rm -f "$rendered" 2>/dev/null || true
      return 0
    fi
  fi

  # 3.5) 覆盖前自检（FIX-C）：目标已存在且与渲染结果有差异 → 打印差异摘要，运维一眼看到本次改了哪几行
  if [ -f "$dst" ]; then
    if diff -q "$rendered" "$dst" >/dev/null 2>&1; then
      log "  站点配置与现有 $dst 无差异"
    else
      log "  检测到站点配置差异（现有 $dst → 本次将写入的版本）："
      diff -u "$dst" "$rendered" 2>/dev/null | sed -n '3,60p' | sed 's/^/    /' || true
    fi
  fi

  # 4) 备份旧配置（失败则放弃同步，避免留下半成品）
  if [ -f "$dst" ]; then
    if ! { mkdir -p "$BACKUP_DIR" && cp -p "$dst" "$bak"; }; then
      err "备份旧站点配置失败（$dst → $bak），为安全起见跳过本次同步"
      rm -f "$rendered" 2>/dev/null || true
      return 0
    fi
    log "  已备份旧站点配置 → $bak"
  fi

  # 5) 落配置（600 root:root，与宝塔 vhost 目录其它 conf 一致；nginx master 以 root 运行可正常读取）
  if ! install -m 600 "$rendered" "$dst"; then
    err "写入站点配置失败：$dst，跳过同步"
    rm -f "$rendered" 2>/dev/null || true
    return 0
  fi
  rm -f "$rendered" 2>/dev/null || true

  # 6) 校验放在 if 判定位：set -e 不会在此终止 → 回滚分支真的会执行（不再成死代码）
  if nginx -t 2>&1; then
    ok "站点配置已同步并校验通过：$dst"
    if nginx -s reload 2>&1; then
      ok "nginx 已 reload"
    else
      err "nginx reload 失败，请手工重载（配置已就位）：nginx -s reload"
    fi
  else
    err "nginx -t 未通过，正在回滚站点配置 ..."
    if [ -f "$bak" ]; then
      if cp -p "$bak" "$dst" 2>/dev/null && nginx -t >/dev/null 2>&1; then
        ok "已回滚到旧配置：$dst"
      else
        err "回滚失败或回滚后仍校验不通过，请手工处理：$dst（旧配置备份在 $bak）"
      fi
    else
      if rm -f "$dst" 2>/dev/null; then
        ok "原无旧配置，已移除刚写入的新配置：$dst"
      else
        err "原无旧配置且移除失败，请手工删除：$dst"
      fi
    fi
    err "nginx 站点配置未生效，已回滚到原状；请手工处理后再重跑本脚本。"
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
# 3. 安装/升级 Node 22 LTS（要求 ^20.19.0 || >=22.12.0）
#    vite 8 / rolldown 1.x 需 Node 20.12+ 的 util.styleText；
#    低于 20.12 的旧版 Node（如 v18.x）会在 vite build 报 "does not provide an export named 'styleText'"
# ------------------------------------------------------------
# 版本判定：满足 ^20.19.0 || >=22.12.0
#   注意 ^20.19.0 仅限 20.x 且 minor>=19；21.x 不在任何范围内（不满足）
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

if command -v node >/dev/null 2>&1; then
  log "检测到已有 Node：$(node -v)（npm $(npm -v)），将升级到 Node 22 LTS ..."
else
  log "未检测到 Node，安装 Node 22 LTS ..."
fi
curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
apt-get install -y nodejs
ok "Node 升级/安装后：$(node -v)（npm $(npm -v)）"

# 安装后强制校验：init 阶段就拦住不满足的版本，不留到构建时才崩
NODE_VER="$(node -v 2>/dev/null || true)"
if ! node_version_satisfies "$NODE_VER"; then
  err "Node 版本不满足要求：当前 ${NODE_VER:-<未安装>}，需要 ^20.19.0 || >=22.12.0"
  err "（原因：vite 8 / rolldown 1.x 依赖 Node 20.12+ 的 util.styleText）"
  err "升级：curl -fsSL https://deb.nodesource.com/setup_22.x | bash - && apt-get install -y nodejs"
  exit 1
fi
ok "Node: $NODE_VER, npm: $(npm -v)"

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
# 写运行环境文件辅助：按行更新 JASYPT_ENCRYPTOR_PASSWORD（其它键原样保留）
#   存在该键 → 替换该行；不存在 → 末尾追加；其余行逐行保留
#   先写临时文件再 mv（同目录 rename 原子），避免整体重写导致丢键 / 半写
# ------------------------------------------------------------
write_env_password() {
  local file="$1" value="$2" tmp
  tmp="$file.tmp-$$"
  if [ -f "$file" ]; then
    awk -v v="$value" '
      $0 ~ /^JASYPT_ENCRYPTOR_PASSWORD=/ { print "JASYPT_ENCRYPTOR_PASSWORD=" v; seen=1; next }
      { print }
      END { if (!seen) print "JASYPT_ENCRYPTOR_PASSWORD=" v }
    ' "$file" > "$tmp" || { rm -f "$tmp" 2>/dev/null || true; return 1; }
  else
    printf 'JASYPT_ENCRYPTOR_PASSWORD=%s\n' "$value" > "$tmp" || { rm -f "$tmp" 2>/dev/null || true; return 1; }
  fi
  chmod 600 "$tmp" 2>/dev/null || true
  mv -f "$tmp" "$file" || { rm -f "$tmp" 2>/dev/null || true; return 1; }
  return 0
}

# ------------------------------------------------------------
# 7. 写运行环境文件（含解密口令，权限 600）
#    fail-fast：.env 已存在且口令与本次不一致时默认中止（口令轮换场景），
#    需显式 FORCE_ENV=1 才覆盖，且覆盖前先备份旧 .env
# ------------------------------------------------------------
if [ -z "${JASYPT_ENCRYPTOR_PASSWORD:-}" ]; then
  err "缺少 JASYPT_ENCRYPTOR_PASSWORD。请这样执行："
  err "  JASYPT_ENCRYPTOR_PASSWORD='你的40位口令' sudo -E bash init-server.sh"
  err "  （口令轮换：JASYPT_ENCRYPTOR_PASSWORD='<新口令>' FORCE_ENV=1 sudo -E bash init-server.sh）"
  err "（该口令用于解密 application.yml 里的 ENC(...) 数据库/Nacos 密码）"
  exit 1
fi

if [ ! -f "$BASE/.env" ]; then
  write_env_password "$BASE/.env" "$JASYPT_ENCRYPTOR_PASSWORD" || { err "写入 $BASE/.env 失败"; exit 1; }
  ok "已写入 $BASE/.env（权限 600）"
else
  cur="$(sed -n 's/^JASYPT_ENCRYPTOR_PASSWORD=//p' "$BASE/.env" | head -1 | tr -d '\r\n')"
  if [ "$cur" = "$JASYPT_ENCRYPTOR_PASSWORD" ]; then
    ok "$BASE/.env 已存在且口令一致，跳过"
  elif [ "${FORCE_ENV:-0}" != "1" ]; then
    err "检测到 $BASE/.env 已存在且口令与本次不一致。这通常是口令轮换场景，已中止以免用旧口令解新密文导致 4 服务全部起不来。"
    err "若确认要轮换，请确认后这样执行："
    err "  JASYPT_ENCRYPTOR_PASSWORD='<新口令>' FORCE_ENV=1 sudo -E bash init-server.sh"
    err "（会先把旧 .env 备份到 $BASE/_backup_<时间戳>/.env.bak 再按行覆盖）"
    exit 1
  else
    mkdir -p "$BACKUP_DIR"
    cp -p "$BASE/.env" "$BACKUP_DIR/.env.bak"
    write_env_password "$BASE/.env" "$JASYPT_ENCRYPTOR_PASSWORD" || { err "更新 $BASE/.env 失败"; exit 1; }
    ok "口令已轮换：旧 .env 已备份到 $BACKUP_DIR/.env.bak，新口令已按行更新到 $BASE/.env（权限 600，其它键保留）"
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
#    含 init-server.sh 自身（幂等可重跑）—— 轮换流程需要它，装到 $BASE 后文档路径才真实存在
# ------------------------------------------------------------
log "放置运维脚本 ..."
for f in init-server.sh restart-backend.sh restart-frontend.sh pull-build-deploy.sh update-frontend.sh smoke-verify.sh; do
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
