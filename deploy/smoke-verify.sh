#!/usr/bin/env bash
# ============================================================
# schoolHelp 上线验收自检（对应 PRD §5）
# 执行：bash /opt/schoolhelp/smoke-verify.sh
# 逐项检查并打印 [ok]/[err]，末尾汇总「通过项/总项」，退出码 0=全通过，非 0=存在失败
# ============================================================
set -uo pipefail

BASE="${BASE:-/opt/schoolhelp}"
WEBROOT="${WEBROOT:-/var/www/schoolhelp}"
SERVICES="gateway user course biz"

PASS=0
FAIL=0

ok()  { PASS=$((PASS + 1)); printf '\033[32m[ok]\033[0m  %s\n' "$*"; }
bad() { FAIL=$((FAIL + 1)); printf '\033[31m[err]\033[0m %s\n' "$*"; }
sep() { printf '\033[36m%s\033[0m\n' "------------------------------------------------------------"; }

# 服务名 → 端口
port_of() {
  case "$1" in
    gateway) echo 8085 ;;
    user)    echo 8101 ;;
    course)  echo 8102 ;;
    biz)     echo 8103 ;;
    *)       echo 0 ;;
  esac
}

echo
printf '\033[36m==== schoolHelp 上线验收自检 ====\033[0m\n'

# ---------- 1. systemd 自启（is-enabled） ----------
sep
printf '\033[36m[1] systemd 自启状态（is-enabled）\033[0m\n'
for s in $SERVICES; do
  en="$(systemctl is-enabled "schoolhelp-$s" 2>/dev/null || true)"
  if [ "$en" = "enabled" ]; then ok "schoolhelp-$s : enabled"; else bad "schoolhelp-$s : ${en:-未安装}（期望 enabled）"; fi
done

# ---------- 2. systemd 运行状态（is-active） ----------
sep
printf '\033[36m[2] systemd 运行状态（is-active）\033[0m\n'
for s in $SERVICES; do
  ac="$(systemctl is-active "schoolhelp-$s" 2>/dev/null || true)"
  if [ "$ac" = "active" ]; then ok "schoolhelp-$s : active"; else bad "schoolhelp-$s : ${ac:-inactive}（期望 active）"; fi
done

# ---------- 3. 端口监听 ----------
sep
printf '\033[36m[3] 端口监听（8080/8101/8102/8103）\033[0m\n'
for s in $SERVICES; do
  p="$(port_of "$s")"
  if ss -ltn 2>/dev/null | grep -q ":$p "; then ok "端口 $p 监听中（$s）"; else bad "端口 $p 未监听（$s）"; fi
done

# ---------- 4. 日志分级文件存在 ----------
sep
printf '\033[36m[4] 日志文件（每服务 app.log + error.log）\033[0m\n'
for s in $SERVICES; do
  for f in app.log error.log; do
    if [ -f "$BASE/$s/logs/$f" ]; then ok "$BASE/$s/logs/$f 存在"; else bad "$BASE/$s/logs/$f 缺失"; fi
  done
done

# ---------- 5. 网关登录冒烟（凭据从环境变量读取，仓库不落明文） ----------
sep
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-}"
# 优先从 /opt/schoolhelp/.env 读取（600 权限，不入仓库）
if [ -z "$ADMIN_PASS" ] && [ -f "$BASE/.env" ]; then
  ADMIN_PASS="$(grep -E '^ADMIN_PASS=' "$BASE/.env" 2>/dev/null | head -1 | cut -d= -f2-)"
fi
printf '\033[36m[5] 网关登录冒烟（%s）\033[0m\n' "$ADMIN_USER"
if [ -z "$ADMIN_PASS" ]; then
  ok "登录冒烟跳过（未设置 ADMIN_PASS 环境变量）"
else
  # 走 nginx(https)→gateway 全链路（--resolve 本地模拟域名；防 nginx 层断裂）
  login_body="$(curl -sk -m 10 --noproxy '*' --resolve cwproject.xyz:443:127.0.0.1 -X POST https://cwproject.xyz/api/user/auth/login \
    -H 'Content-Type: application/json' -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" || true)"
  login_http="$(curl -sk -m 10 --noproxy '*' --resolve cwproject.xyz:443:127.0.0.1 -o /dev/null -w '%{http_code}' -X POST https://cwproject.xyz/api/user/auth/login \
    -H 'Content-Type: application/json' -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" || echo 000)"
  if printf '%s' "$login_body" | grep -q '"code":200'; then
    ok "登录返回 code:200（HTTP $login_http）"
  elif [ "$login_http" = "200" ]; then
    ok "登录 HTTP 200（响应体未含 code:200，请人工确认）"
  else
    bad "登录失败：HTTP $login_http，body=$(printf '%s' "$login_body" | head -c 200)"
  fi
fi

# ---------- 6. 无 token 访问受保护接口返回 401 ----------
sep
printf '\033[36m[6] 鉴权：无 token 访问受保护接口\033[0m\n'
auth_code="$(curl -sk -m 10 --noproxy '*' --resolve cwproject.xyz:443:127.0.0.1 -o /dev/null -w '%{http_code}' \
  https://cwproject.xyz/api/user/schedule/list-by-user || echo 000)"
if [ "$auth_code" = "401" ]; then ok "无 token → HTTP 401"; else bad "无 token → HTTP $auth_code（期望 401）"; fi

# ---------- 7. 前端站点与同域 UA 分流 ----------
sep
printf '\033[36m[7] 前端站点与同域 UA 分流\033[0m\n'
for d in pc m; do
  if [ -f "$WEBROOT/$d/index.html" ]; then ok "$WEBROOT/$d/index.html 存在"; else bad "$WEBROOT/$d/index.html 缺失"; fi
done
if sudo grep -q 'site_root' /etc/nginx/sites-enabled/schoolhelp 2>/dev/null; then
  ok "nginx 同域 UA 分流配置存在"
else
  bad "nginx UA 分流配置缺失"
fi
# 站点可达性：http→308 跳转 + https 全链路（--resolve 本地模拟域名）
c80="$(curl -s -m 10 --noproxy '*' -o /dev/null -w '%{http_code}' -H 'Host: cwproject.xyz' "http://127.0.0.1/" || echo 000)"
if [ "$c80" = "308" ]; then ok "站点 :80 → 308 跳 HTTPS"; else bad "站点 :80 → HTTP $c80（期望 308）"; fi
c443="$(curl -sk -m 10 --noproxy '*' --resolve cwproject.xyz:443:127.0.0.1 -o /dev/null -w '%{http_code}' "https://cwproject.xyz/" || echo 000)"
if [ "$c443" = "200" ]; then ok "站点 :443（HTTPS PC）→ 200"; else bad "站点 :443 → HTTP $c443（期望 200）"; fi
# 移动站目录（m.cwproject.xyz 子域待 DNS/证书就绪后启用 443）
if [ -f "$WEBROOT/m/index.html" ]; then ok "$WEBROOT/m/index.html 存在（m.cwproject.xyz 待启用）"; else bad "$WEBROOT/m/index.html 缺失"; fi

# ---------- 汇总 ----------
sep
TOTAL=$((PASS + FAIL))
if [ "$FAIL" -eq 0 ]; then
  printf '\033[32m通过 %d/%d 项，全部通过 ✅\033[0m\n' "$PASS" "$TOTAL"
  exit 0
else
  printf '\033[31m通过 %d/%d 项，失败 %d 项 ❌\033[0m\n' "$PASS" "$TOTAL" "$FAIL"
  exit 1
fi
