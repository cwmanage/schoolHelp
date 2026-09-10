# schoolHelp 服务器运维脚本说明

> 目录：`deploy/`　　目标服务器：Ubuntu 22.04/24.04（香港机 47.83.169.101）

## 脚本一览

| 脚本 | 作用 | 何时用 |
|------|------|--------|
| `init-server.sh` | **一次性初始化**：装依赖(git/maven/node/rsync)、建目录、写 `.env`、装 systemd、放置其余脚本、克隆仓库 | 新服务器第一次用 |
| `restart-backend.sh` | **一键重启后端**（4 个 jar） | 改过后端 / 想让后端重启 |
| `restart-frontend.sh` | **一键重启前端**（Nginx reload + 站点检查） | 前端目录更新后 |
| `pull-build-deploy.sh` | **一键拉取+打包+部署**（后端 jar + 前端 dist 全量） | 代码推送到 GitHub 后，服务器一键上线 |
| `update-frontend.sh` | **一键更新前端**（只构建前端，不动后端） | 只改了 Vue 页面时 |

---

## 一、部署方式与依赖包清单

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
| 4 个后端 jar | `schoolhelp-{gateway,user,course,biz}/target/schoolhelp-*.jar` | `/opt/schoolhelp/app/` |
| PC 端 dist | `schoolhelp-web-pc/dist/` | `/var/www/schoolhelp/pc/` |
| 移动端 dist | `schoolhelp-web/dist/` | `/var/www/schoolhelp/m/` |
| SQL 增量脚本 | `sql/v3_user_approve_migration.sql` | 手动导入数据库 |

此时 `update-frontend.sh` 可用 `NO_PULL=1` 跳过 git 拉取。

---

## 二、首次部署步骤（方式 A）

```bash
# 0) 从本地把整个 deploy 目录传上去
#    本地 PowerShell：
#    scp -r D:\homework\schoolHelp\schoolHelp\deploy root@47.83.169.101:/root/

# 1) 服务器：初始化（必须带上解密口令，用于还原配置里的 ENC 密码）
cd /root/deploy
JASYPT_ENCRYPTOR_PASSWORD='hMdBvBbPmXguZn3Nh2FSNsB6K3hgTfMLzSZij7E3' sudo -E bash init-server.sh

# 2) 导入数据库增量脚本（班长审批字段）
cd /opt/schoolhelp/src
mysql -u sgtxgx -p'sgtxgx123!' schoolhelpdb < sql/v3_user_approve_migration.sql

# 3) 一键部署（拉代码→打包→铺前端→重启）
bash /opt/schoolhelp/pull-build-deploy.sh
```

> ⚠️ 口令 `hMdBvBbPmXguZn3Nh2FSNsB6K3hgTfMLzSZij7E3` 是解密 `application.yml` 里数据库/Nacos 密码用的，必须一字不差，且只写在服务器 `/opt/schoolhelp/.env`（600 权限），不要提交到仓库。

---

## 三、日常用法

```bash
# 改了后端代码并 push 到 GitHub 后：
bash /opt/schoolhelp/pull-build-deploy.sh

# 只改了前端页面：
bash /opt/schoolhelp/update-frontend.sh
bash /opt/schoolhelp/update-frontend.sh                    # 两端都更
TARGET=m bash /opt/schoolhelp/update-frontend.sh           # 只更移动端
TARGET=pc NO_PULL=1 bash /opt/schoolhelp/update-frontend.sh # 只更PC且不拉代码

# 单独重启后端 / 前端：
bash /opt/schoolhelp/restart-backend.sh
bash /opt/schoolhelp/restart-frontend.sh
```

---

## 四、端口与站点约定

| 项目 | 端口 | 说明 |
|------|------|------|
| PC 端前端 | **80** | 主入口，手机访问会自动跳 8081 |
| 移动端前端 | **8081** | 手机/平板访问；PC 访问会跳回 80 |
| 网关 gateway | 8080 | Nginx 反代 `/api` → 8080 |
| user / course / biz | 8101 / 8102 / 8103 | 仅内网，不对公网 |
| Nacos | 8848 | 仅 127.0.0.1，建议走 SSH 隧道 |

**设备自动分流**：两端 `index.html` 内置 UA 检测脚本。电脑访问 8081 会自动跳 80，手机访问 80 会自动跳 8081；想强行停留，分别在网址后加 `?pc=1`（PC）、`?mobile=1`（移动）。

---

## 五、常见问题

- **服务起不来**：`journalctl -u schoolhelp-user -n 100` 看日志；多数是 `.env` 口令与密文不匹配。
- **401 全挂**：JWT 密钥问题（`JwtUtil.SECRET` 目前硬编码，上线前建议外置）。
- **前端 404/白屏**：确认 Nginx `www` 指向 `/var/www/schoolhelp/pc`，且 `router` 用 history 模式已在 Nginx 配 `try_files ... /index.html`。
- **端口没监听**：`ss -ltnp | grep -E ':8080|:8101|:8102|:8103'`。
