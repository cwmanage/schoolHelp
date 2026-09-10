# schoolHelp 校园助手

> 哈尔滨学院专属校园助手 —— 课表、课程库、作业提醒、课程资料、课程评论一站通。
> 单人/小团队可跑起来的 Spring Cloud 微服务练手项目（研一练手，非商业产品）。

---

## 一、这是什么

一个给同班/同专业同学用的小工具，解决三件事：

1. **我的课表**：可视化周课表（周视图），手动录入，支持单双周、节次、教室。
2. **课程库 + 作业**：统一的课程信息、作业截止时间；临期作业自动在首页红色条幅滚动提醒。
3. **资料共享 + 评论**：课程资料上传下载、课程评论区（支持匿名，管理员可追溯真实身份）。

所有"同学提交的内容"都走**审批流**，管理员把关，先审后展示。

---

## 二、技术栈

| 层 | 技术 | 说明 |
|----|------|------|
| 语言/运行时 | Java 17 | Spring Boot 3 要求 Java 17+ |
| 后端框架 | Spring Boot 3.2.4 | 单体拆微服务的骨架 |
| 微服务 | Spring Cloud 2023.0.1 + Spring Cloud Alibaba 2023.0.1.0 | Nacos 做注册中心 |
| 网关 | Spring Cloud Gateway | 统一入口、JWT 校验、路由、CORS |
| 服务调用 | OpenFeign | 服务间调用走 Nacos 服务发现 |
| ORM | MyBatis-Plus 3.5.7 | 单表 CRUD 免写 XML |
| 数据库 | MySQL 8 | 库名 `schoolhelpdb`，字符集 utf8mb4 |
| 认证 | JWT（Hutool 签发）+ BCrypt | 无状态鉴权，密码强度四档 |
| 前端 | Vue 3 + Vite + Element Plus + Pinia + Vue Router + axios | 双端：移动端 + PC 端 |
| 天气 | Open-Meteo 免费 API | 免 key，坐标写死哈尔滨学院 |

---

## 三、工程结构

```
schoolHelp/
├── pom.xml                      # 父 POM（聚合 5 模块，统一依赖版本）
├── start-all.ps1                # 本地一键启动 4 个后端服务
├── sql/
│   ├── v1_schema.sql            # v1 建表脚本（7 张表）
│   └── v2_approval_migration.sql# v2 审批流增量迁移
├── schoolhelp-common/           # 公共模块：Result / 异常 / 常量 / JWT / 用户上下文
├── schoolhelp-gateway/          # 网关 :8080（唯一对外入口）
├── schoolhelp-user/             # 用户 + 课表服务 :8101
├── schoolhelp-course/           # 课程 + 作业 + 资料服务 :8102
├── schoolhelp-biz/              # 业务聚合服务 :8103（临期作业、评论、提交标记）
├── schoolhelp-web/              # 移动端前端（Vite dev :5173）
└── schoolhelp-web-pc/           # PC 端前端（Vite dev :5174）
```

**5 个后端模块说明**

| 模块 | 端口 | 职责 |
|------|------|------|
| common | — | 被其他模块依赖：`Result` 统一响应、`BusinessException`、`GlobalExceptionHandler`、`CommonConstants`、`JwtUtil`、`UserContext`、`ReviewDTO` |
| gateway | 8080 | 路由转发、JWT 校验（`AuthGlobalFilter`，order=-100）、白名单、CORS；对外只暴露这一个端口 |
| user | 8101 | 注册/登录/用户资料/密码修改、个人课表 CRUD |
| course | 8102 | 课程库、作业、课程资料（含审批状态机） |
| biz | 8103 | 临期作业聚合（Feign 调 user+course）、课程评论、作业提交标记 |

> gateway 通过 `StripPrefix` 去掉 `/api` 前缀后转发；例如 `POST /api/user/auth/login` → `user` 服务 `/auth/login`。

---

## 四、快速开始

### 0. 前置条件

- JDK 17（本项目用 `C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot`）
- Maven（可用自带 `mvnw`）
- MySQL 8（本地 3306）
- Nacos 2.x（本地 8848）
- Node.js 18+（前端）
- 环境变量 `JASYPT_ENCRYPTOR_PASSWORD`（解密配置文件里的 `ENC(...)` 密文，详见「6. 配置加密」）

### 1. 初始化数据库

```sql
CREATE DATABASE schoolhelpdb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

然后依次执行：

```bash
mysql -uroot -p schoolhelpdb < sql/v1_schema.sql
mysql -uroot -p schoolhelpdb < sql/v2_approval_migration.sql
```

> 注意：`v2_approval_migration.sql` 首行是 `#` 注释（MySQL 支持），在**已执行 v1 之后**执行；脚本为增量幂等设计（表为空时可直接跑）。

### 2. 启动 Nacos

本地启动 Nacos（默认 `8848`），确保控制台可访问。

### 3. 打包并启动后端

```powershell
# 一键打包（生成各模块 target/*.jar）
.\mvnw.cmd -DskipTests package

# 一键启动 4 个服务 + 端口检查 + 冒烟测试
powershell -ExecutionPolicy Bypass -File .\start-all.ps1
```

`start-all.ps1` 会依次启动 gateway/user/course/biz，等 30 秒后检查端口，并自动跑一次"注册 → 登录"冒烟测试。日志在 `logs/`。

启动成功后：

| 服务 | 地址 |
|------|------|
| 网关（所有 API 入口） | http://127.0.0.1:8080 |
| Nacos 控制台 | http://127.0.0.1:8848/nacos |

### 4. 启动前端

```bash
# 移动端
cd schoolhelp-web
npm install
npm run dev        # http://127.0.0.1:5173 （登录后进 /schedule）

# PC 端
cd schoolhelp-web-pc
npm install
npm run dev        # http://127.0.0.1:5174 （登录后进 /）
```

前端通过 Vite 代理把 `/api` 转发到 `http://127.0.0.1:8080`。

### 5. 默认账号

| 账号 | 密码 | 角色 |
|------|------|------|
| `admin` | `admin123` | 管理员（首次启动自动创建，**请尽快改密**） |

同学/班长账号自行注册；班长身份需管理员在库中把 `user.role` 改为 1。

### 6. 配置加密（敏感信息）

配置文件中**数据库密码 / Nacos 密码**不以明文存储，而是用 **Jasypt** 加密后的 `ENC(...)` 密文，例如：

```yaml
spring:
  datasource:
    password: "ENC(3RLRaMv4jKsDmW/TFjBut3ZXBxC7vftbGPo/MvJchJB1vpiJwObBDeSwM/SCHmS3)"
```

解密口令（加/解密密钥）**不写在任何入仓库的文件里**，通过环境变量 `JASYPT_ENCRYPTOR_PASSWORD` 注入：

- 本地开发：在项目根目录新建 `.env.local`，内容一行 `JASYPT_ENCRYPTOR_PASSWORD=<你的口令>`。`start-all.ps1` 会自动读取它；该文件已被 `.gitignore` 忽略，不会进仓库。
- 手动启动：先 `$env:JASYPT_ENCRYPTOR_PASSWORD="<你的口令>"`，再 `java -jar ...`。
- 服务器部署：用 systemd `Environment=`、Docker `-e`、或启动脚本 `export` 注入。

> ⚠️ 换了加密口令，需要重新生成所有密文（见 `代码健壮性走查报告.md` 修复说明）。口令丢了 → 密文解不开 → 服务起不来。

`salt-generator-classname` 用 `RandomSaltGenerator`（同一明文每次加密得到的密文不同），`algorithm` 为 `PBEWITHHMACSHA512ANDAES_256`。

---

## 五、核心业务流程

### 审批流（v2，重点）

`course` / `assignment` / `course_material` 三张表都带审批字段，状态机：

```
0 待审批(pending) ──审批通过──> 1 已通过(approved)  ← 公开列表只展示这个
        └──审批驳回──> 2 已驳回(rejected) ──修改后重提──> 0
课程另有 3 下架(offline)
```

- **谁能直接通过**：班长、管理员录入的内容直接 `status=1`（免审）。
- **谁要审批**：普通同学申请的内容 `status=0`，等管理员审批。
- **谁能改**：管理员/班长可改并保持状态；**普通同学改自己已通过的内容会被打回 `status=0` 重新审批**（防绕过审核）。
- **谁能删**：管理员任意删；普通同学只能删自己"未通过"的申请。
- **审批**：仅管理员；驳回**必须填原因**。

### 临期作业条幅

逻辑：**我的课表（有 courseId 的课）∩ 作业截止 ≤ 5 天 ∩ 我未标记提交** → 首页红色条幅滚动。
由 `biz` 的 `UrgentAssignmentService` 通过 Feign 聚合 `user`（我的课表）和 `course`（课程作业）实现。

### 匿名评论

- 同学视角：匿名评论显示"匿名同学 + 黑客头像"，且接口直接把 `userId` 置 `null` 不返回。
- 管理员视角：可通过 `/comment/admin/{commentId}` 查到真实 `user_id`。
- 删除为逻辑删除（`status=0`），保留可追溯证据。

---

## 六、部署说明

当前部署环境（演练用，非商业）：

- **开发机（本地）**：全部服务跑在 127.0.0.1，前后端分离。
- **线上（可选）**：一台云服务器 + Nginx。

**Nginx 反代要点**（以移动端为例）：

```nginx
server {
    listen 80;
    server_name your-domain-or-ip;

    # 前端静态资源
    location / {
        root /var/www/schoolhelp-web;
        try_files $uri $uri/ /index.html;   # SPA history 路由必须
    }

    # 后端 API 统一走网关
    location /api/ {
        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

**备案提示**：大陆服务器（含阿里云大陆地域）对外提供 Web 服务需 ICP 备案；香港/境外服务器无需备案，可直接用 `IP:端口` 访问（仅 HTTP，无 HTTPS 证书）。

---

## 七、常见问题（排障备忘）

| 现象 | 原因/解决 |
|------|-----------|
| 网关 503 / 找不到服务 | 服务未注册到 Nacos。检查各服务启动日志、Nacos 服务列表 |
| 接口 401 | 没带 `Authorization: Bearer <token>`，或 token 过期 |
| 列表接口 405 | 路径写错（如误用 `/course/list`，正确为 `/course/course/list`） |
| 数据库中文变 `????` | 写入源头编码错误导致脏数据。**用 `SELECT HEX(字段)` 查库确认**，别信控制台乱码 |
| 前端 502 | 多为工具（.NET/curl）带 `Expect: 100-continue` 触发，真实浏览器不受影响 |
| 课表页空白 | 检查 `Schedule.vue` 是否漏 `import { reactive }` 之类的引用错误 |
| 启动报 `Files not found` / `password` 解密失败 / `Unable to decrypt` | 没设 `JASYPT_ENCRYPTOR_PASSWORD`，或口令与加密时不一致。检查 `.env.local` / 环境变量 |
| MySQL `Access denied for user 'sgtxgx'` | 同上：密文解密后得到的密码不对（口令错）或数据库密码已变，需重新生成密文 |

---

## 八、说明

- 本项目的产品定位、需求与代码走查结论，见根目录另三份文档：
  - `开发日志.md`
  - `需求文档.md`
  - `代码健壮性走查报告.md`
- 仅供学习/校内小范围使用，请遵守学校规定与相关法律法规。
