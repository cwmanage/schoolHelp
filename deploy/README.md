# schoolHelp 服务器运维脚本说明

> 目录：`deploy/`　　目标服务器：Ubuntu 22.04/24.04（香港机 47.83.169.101）

## 脚本一览

| 脚本 | 作用 | 何时用 |
|------|------|--------|
| `init-server.sh` | **一次性初始化**：装依赖(git/maven/node/rsync)、停旧裸进程、备份旧文件、逐服务建目录、写 `.env`、渲染 systemd 具名单元、放置其余脚本、克隆仓库 | 新服务器第一次用 |
| `restart-backend.sh` | **一键重启后端**（4 个 systemd 服务） | 改过后端 / 想让后端重启 |
| `restart-frontend.sh` | **一键重启前端**（Nginx reload + 站点检查） | 前端目录更新后 |
| `pull-build-deploy.sh` | **一键拉取+打包+部署**（后端 jar + 前端 dist 全量，原子替换） | 代码推送到 GitHub 后，服务器一键上线 |
| `update-frontend.sh` | **一键更新前端**（只构建前端，不触发 Maven） | 只改了 Vue 页面时 |
| `smoke-verify.sh` | **上线验收自检**（按 PRD §5 逐项检查并打印 `[ok]/[err]`） | 部署完成后跑一次 |

---

## 一、服务器目录约定（N1：每服务独立目录，无 `app` 中间层）

```text
/opt/schoolhelp/                     # BASE
├── .env                             # 600，仅一行 JASYPT_ENCRYPTOR_PASSWORD=<口令>
├── *.sh                             # 运维脚本（755，root 直跑）
├── sql/                             # 数据库迁移脚本
├── src/                             # git clone 的仓库（mvn/npm 在此构建）
├── _backup_<日期>/                  # 旧 jar / 旧 log 的备份
├── gateway/  schoolhelp-gateway.jar  + logs/ + uploads/
├── user/     schoolhelp-user.jar     + logs/ + uploads/
├── course/   schoolhelp-course.jar   + logs/ + uploads/
└── biz/      schoolhelp-biz.jar      + logs/ + uploads/
```

> 规则：`sql/`、`*.sh`、`.env`、`src/`、`_backup_*/` 留在 `BASE` 根；**只有 jar、`logs/`、`uploads/` 进入 `BASE/<服务>/`**。

| 服务 | 端口 | 目录 | unit |
|------|------|------|------|
| gateway | 8080 | `/opt/schoolhelp/gateway/` | `schoolhelp-gateway.service` |
| user | 8101 | `/opt/schoolhelp/user/` | `schoolhelp-user.service` |
| course | 8102 | `/opt/schoolhelp/course/` | `schoolhelp-course.service` |
| biz | 8103 | `/opt/schoolhelp/biz/` | `schoolhelp-biz.service` |

**进程托管**：4 个后端由 **systemd 具名单元**托管（`Restart=always` + `WantedBy=multi-user.target`），开机自启、崩溃自愈，不再依赖 SSH 会话。

---

## 二、日志分级与排障

日志由 `logback-spring.xml`（位于 `schoolhelp-common`，4 服务共享）统一接管：

| 文件 | 内容 | 路径 |
|------|------|------|
| `app.log` | **INFO 及以上全量** | `/opt/schoolhelp/<服务>/logs/app.log` |
| `error.log` | **仅 ERROR** | `/opt/schoolhelp/<服务>/logs/error.log` |

- **按天滚动**：历史文件为 `app.<yyyy-MM-dd>.log` / `error.<yyyy-MM-dd>.log`。
- **保留 14 天**（可通过环境变量 `LOG_RETENTION_DAYS` 调整），并设有 `totalSizeCap` 兜底。
- 服务器日志目录由 systemd 注入 `-DLOG_DIR=/opt/schoolhelp/<服务>/logs`（绝对路径，不依赖进程 CWD）。
- 本地开发不注入 → 落到相对路径 `logs/<spring.application.name>/`，4 服务各自子目录、互不覆盖。
- systemd 的 stdout/stderr 走 **journald**（`StandardOutput=journal`），保留 logback 初始化之前的原始输出，**不与文件日志双写**。
- MyBatis 已从 `StdOutImpl` 改为 `Slf4jImpl`，SQL 受日志级别控制，不再绕过日志框架刷屏。

**排障命令**：

```bash
# 服务状态 / 重启（<服务> = gateway|user|course|biz）
systemctl status  schoolhelp-<服务> -n 50
systemctl restart schoolhelp-<服务>

# journald（logback 初始化前的原始 stdout）
journalctl -u schoolhelp-<服务> -n 100
journalctl -u schoolhelp-<服务> -f

# 文件日志（分级）
tail -f /opt/schoolhelp/<服务>/logs/app.log
tail -f /opt/schoolhelp/<服务>/logs/error.log
```

---

## 三、部署方式与依赖包清单

### 方式 A（推荐）：服务器上自动构建

**服务器需要联网**，且由 `init-server.sh` 自动安装以下依赖，**无需手动上传任何依赖包**：

| 依赖 | 版本 | 用途 | 安装方式 |
|------|------|------|----------|
| git | 系统自带 | 拉取代码 | apt |
| JDK | 17 | 运行 + 编译后端 | apt / 已装 |
| Maven | 3.6+ | 打包后端 | apt |
| Node.js | 18 LTS | 构建前端 | NodeSource |
| npm 依赖 | 见 package.json | 前端构建 | `npm install` 自动拉（已配淘宝镜像加速） |
| Maven 依赖 | 见各 pom.xml | 后端打包 | Maven 自动拉（已配阿里云镜像） |

- Maven 依赖会下载到服务器 `~/.m2/repository`（首次约 200–400MB，之后就缓存了）。
- npm 依赖下载到各前端目录 `node_modules/`（每个约 110MB）。
- **这两个目录都不要上传、不要提交 git**，让服务器自己拉即可。

### 方式 B（备选）：本地构建后只传产物

服务器不联网 / 不想装 Maven、Node 时，用本地已构建好的产物：

| 上传内容 | 本地路径 | 服务器目标 |
|----------|----------|------------|
| 4 个后端 jar | `schoolhelp-{gateway,user,course,biz}/target/schoolhelp-*.jar` | `/opt/schoolhelp/<服务>/` |
| PC 端 dist | `schoolhelp-web-pc/dist/` | `/var/www/schoolhelp/pc/` |
| 移动端 dist | `schoolhelp-web/dist/` | `/var/www/schoolhelp/m/` |
| SQL 增量脚本 | `sql/v3_user_approve_migration.sql` | 手动导入数据库 |

此时 `update-frontend.sh` 可用 `NO_PULL=1` 跳过 git 拉取。

---

## 四、首次部署步骤（方式 A）

```bash
# 0) 从本地把整个 deploy 目录传上去（含 systemd/ 子目录）
#    本地 PowerShell：
#    scp -r D:\homework\schoolHelp\schoolHelp\deploy root@47.83.169.101:/root/

# 1) 服务器：初始化（必须带上解密口令，用于还原配置里的 ENC 密码）
#    口令只写在服务器 /opt/schoolhelp/.env（600），不要提交到仓库
cd /root/deploy
JASYPT_ENCRYPTOR_PASSWORD='<你的40位JASYPT口令>' sudo -E bash init-server.sh

# 2) 导入数据库增量脚本（班长审批字段）
cd /opt/schoolhelp/src
mysql -u sgtxgx -p'<数据库口令>' schoolhelpdb < sql/v3_user_approve_migration.sql

# 3) 一键部署（拉代码→打包→原子铺前端→重启）
bash /opt/schoolhelp/pull-build-deploy.sh

# 4) 验收自检
bash /opt/schoolhelp/smoke-verify.sh
```

> ⚠️ `JASYPT_ENCRYPTOR_PASSWORD` 是解密 `application.yml` 里数据库/Nacos 密码用的，必须一字不差，且**只写在服务器 `/opt/schoolhelp/.env`（600 权限）**，任何入仓库的文件都不得出现明文口令。

---

## 五、日常用法

```bash
# 改了后端代码并 push 到 GitHub 后：
bash /opt/schoolhelp/pull-build-deploy.sh

# 只改了前端页面（不触发 Maven）：
bash /opt/schoolhelp/update-frontend.sh                    # 两端都更
TARGET=m bash /opt/schoolhelp/update-frontend.sh           # 只更移动端
TARGET=pc NO_PULL=1 bash /opt/schoolhelp/update-frontend.sh # 只更PC且不拉代码

# 单独重启后端 / 前端：
bash /opt/schoolhelp/restart-backend.sh
bash /opt/schoolhelp/restart-frontend.sh
```

### 脚本参数接口

| 脚本 | 环境变量 | 取值 | 默认 |
|------|----------|------|------|
| `init-server.sh` | `JASYPT_ENCRYPTOR_PASSWORD` | 40 位口令 | **必填** |
| | `REPO_URL` / `BRANCH` | URL / 分支 | 仓库地址 / `master` |
| `pull-build-deploy.sh` | `BRANCH` | 分支 | `master` |
| | `SKIP_BACKEND` / `SKIP_FRONTEND` | `1` 跳过 | 不跳过 |
| | `MAVEN_MIRROR` / `NPM_REGISTRY` | URL | 阿里云 / 淘宝 |
| `update-frontend.sh` | `TARGET` | `both`\|`pc`\|`m` | `both` |
| | `NO_PULL` | `1` 不拉码 | 拉码 |
| | `BRANCH` / `NPM_REGISTRY` | 同上 | `master` / 淘宝 |

**退出码约定**：`0` = 成功，非 `0` = 失败；输出统一 `[ok]`/`[err]` 前缀。

---

## 六、端口与站点约定

| 项目 | 端口 | 说明 |
|------|------|------|
| PC 端前端 | **80** | 主入口，手机访问会自动跳 8081 |
| 移动端前端 | **8081** | 手机/平板访问；PC 访问会跳回 80 |
| 网关 gateway | 8080 | Nginx 反代 `/api` → 8080 |
| user / course / biz | 8101 / 8102 / 8103 | 仅内网，不对公网 |
| Nacos | 8848 | 仅 127.0.0.1，建议走 SSH 隧道 |

**设备自动分流**：两端 `index.html` 内置 UA 检测脚本。电脑访问 8081 会自动跳 80，手机访问 80 会自动跳 8081；想强行停留，分别在网址后加 `?pc=1`（PC）、`?mobile=1`（移动）。

---

## 七、验收命令（对应 PRD §5）

```bash
# 4 个服务自启状态
systemctl is-enabled schoolhelp-gateway schoolhelp-user schoolhelp-course schoolhelp-biz  # 期望 4× enabled

# 4 个端口监听
ss -ltnp | grep -E ':8080|:8101|:8102|:8103'

# 日志分级文件存在
ls /opt/schoolhelp/<服务>/logs/{app.log,error.log}

# 回归基线
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://127.0.0.1:8080/api/user/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}'    # 期望 200
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/user/schedule/list-by-user  # 期望 401
curl -s http://47.83.169.101/ | grep -c force_pc           # 期望 >= 1
curl -s http://47.83.169.101:8081/ | grep -c force_mobile  # 期望 >= 1

# 一键自检
bash /opt/schoolhelp/smoke-verify.sh
```

---

## 八、常见问题

- **服务起不来**：`journalctl -u schoolhelp-user -n 100` 看日志；多数是 `.env` 口令与密文不匹配。
- **401 全挂**：JWT 密钥问题（`JwtUtil.SECRET` 目前硬编码，上线前建议外置）。
- **前端 404/白屏**：确认 Nginx `www` 指向 `/var/www/schoolhelp/pc`，且 `router` 用 history 模式已在 Nginx 配 `try_files ... /index.html`。
- **端口没监听**：`ss -ltnp | grep -E ':8080|:8101|:8102|:8103'`。
- **迁移后旧 jar 误启动**：旧 jar/旧 log 已被 `init-server.sh` 备份到 `/opt/schoolhelp/_backup_<日期>/`，确认新部署稳定后可自行删除。
