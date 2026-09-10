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
    gateway) echo 8080 ;;
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

# ---------- 5. 网关登录（admin/admin123）返回 code:200 ----------
sep
printf '\033[36m[5] 网关登录冒烟（admin/admin123）\033[0m\n'
login_body="$(curl -s -m 10 --noproxy '*' -X POST http://127.0.0.1:8080/api/user/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' || true)"
login_http="$(curl -s -m 10 --noproxy '*' -o /dev/null -w '%{http_code}' -X POST http://127.0.0.1:8080/api/user/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' || echo 000)"
if printf '%s' "$login_body" | grep -q '"code":200'; then
  ok "登录返回 code:200（HTTP $login_http）"
elif [ "$login_http" = "200" ]; then
  ok "登录 HTTP 200（响应体未含 code:200，请人工确认）"
else
  bad "登录失败：HTTP $login_http，body=$(printf '%s' "$login_body" | head -c 200)"
fi

# ---------- 6. 无 token 访问受保护接口返回 401 ----------
sep
printf '\033[36m[6] 鉴权：无 token 访问受保护接口\033[0m\n'
auth_code="$(curl -s -m 10 --noproxy '*' -o /dev/null -w '%{http_code}' \
  http://127.0.0.1:8080/api/user/schedule/list-by-user || echo 000)"
if [ "$auth_code" = "401" ]; then ok "无 token → HTTP 401"; else bad "无 token → HTTP $auth_code（期望 401）"; fi

# ---------- 7. 前端站点分流脚本 ----------
sep
printf '\033[36m[7] 前端站点与设备分流脚本\033[0m\n'
for d in pc m; do
  if [ -f "$WEBROOT/$d/index.html" ]; then ok "$WEBROOT/$d/index.html 存在"; else bad "$WEBROOT/$d/index.html 缺失"; fi
done
if [ -f "$WEBROOT/pc/index.html" ]; then
  if grep -q 'force_pc' "$WEBROOT/pc/index.html"; then ok "PC 站含 force_pc"; else bad "PC 站不含 force_pc"; fi
fi
if [ -f "$WEBROOT/m/index.html" ]; then
  if grep -q 'force_mobile' "$WEBROOT/m/index.html"; then ok "移动站含 force_mobile"; else bad "移动站不含 force_mobile"; fi
fi
# 站点 HTTP 可达性
for p in 80 8081; do
  c="$(curl -s -m 10 --noproxy '*' -o /dev/null -w '%{http_code}' "http://127.0.0.1:$p/" || echo 000)"
  if [ "$c" = "200" ]; then ok "站点 :$p → HTTP 200"; else bad "站点 :$p → HTTP $c（期望 200）"; fi
done

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
