# 课屿 Keyu（schoolHelp 校园助手）

> 哈尔滨学院专属校园助手 —— 课表、课程库、作业提醒、课程资料、课程评论、**AI 助教**一站通。
> 品牌名「课屿」：课程如屿、学海同航；仓库名/包名/服务名沿用 schoolHelp。
> 单人/小团队可跑起来的 Spring Cloud 微服务练手项目（研一练手，非商业产品）。

---

## 一、这是什么

一个给同班/同专业同学用的小工具，核心能力：

1. **我的课表（v3 大幅增强）**：可视化周课表；每节课显示起止时间（作息配置 + **AI 生成作息建议**）；当前第几周、周翻页查看任意周、非本周课程虚化；连堂课合并展示、同课程多时段同色；**拖拽调课**（临时=本周 / 永久=本学期）；底部「大学活动」行展示当天考试/竞赛/节假日。
2. **课程库 + 作业**：统一的课程信息、作业截止时间；临期作业自动在首页红色条幅滚动提醒。
3. **资料共享 + 评论**：课程资料上传下载、课程评论区（支持匿名，管理员可追溯真实身份）。
4. **AI 助教（v3 新增）**：接入智谱免费大模型（GLM-4-Flash / GLM-4V-Flash）——侧边对话问答（Markdown 渲染）、**课表截图拍照识别批量导入**、导入时批量申请录入课程库。
5. **校园日历（v3 新增）**：每日定时任务同步节假日（免费 API）+ AI 生成考试/竞赛日历；登录后有更新弹提示；课表与首页展示。
6. **七天免登录（v3 新增）**：JWT 滑动续期——7 天内有任意访问即保持登录。

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
| AI | 智谱开放平台 GLM-4-Flash / GLM-4V-Flash | **免费模型**；OpenAI 兼容接口，服务端代理转发，key 用 Jasypt 加密存储 |
| 校园日历 | timor.tech 节假日免费 API + 大模型生成考试/竞赛日历 | 服务端每日定时任务同步（03:10 + 启动时） |
| 天气 | Open-Meteo 免费 API | 免 key，坐标写死哈尔滨学院 |

---

## 三、工程结构

```
schoolHelp/
├── pom.xml                      # 父 POM（聚合 5 模块，统一依赖版本）
├── start-all.ps1                # 本地一键启动 4 个后端服务
├── sql/
│   ├── v1_schema.sql              # v1 建表脚本（7 张表）
│   ├── v2_approval_migration.sql  # v2 审批流增量迁移
│   └── （服务器侧另有 v3 班长审批 / v4 作息配置 / v5 课表升级迁移，见 deploy/sql/）
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
| user | 8101 | 注册/登录/用户资料/密码修改、个人课表 CRUD、**作息配置 / 学期设置 / 调课 / 校园日历 / AI 助教代理** |
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
- Node.js 22 LTS（前端，要求 `^20.19.0 || >=22.12.0`；低于此版本 `vite build` 会报 `does not provide an export named 'styleText'`）
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

> v3 功能需要的增量表（作息配置 / 学期设置 / 校园日历 / 调课记录）与课程库教室列，见 `deploy/sql/v4_schedule_time_config.sql`、`deploy/sql/v5_schedule_upgrade.sql`（均幂等可重复执行；班长审批迁移 `v3_user_approve_migration.sql` 同目录）。

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

| 账号      | 密码       | 角色 |
|---------|----------|-----|
| `*****` | `******` | 管理员 |

同学/班长账号自行注册；班长身份需管理员审批。

### 6. 配置加密（敏感信息）

配置文件中**数据库密码 / Nacos 密码**不以明文存储，而是用 **Jasypt** 加密后的 `ENC(...)` 密文，例如：

```yaml
spring:
  datasource:
    password: "ENC(<Jasypt 加密后的 Base64 密文>)"
```

解密口令（加/解密密钥）**不写在任何入仓库的文件里**，通过环境变量 `JASYPT_ENCRYPTOR_PASSWORD` 注入：

- 本地开发：在项目根目录新建 `.env.local`，内容一行 `JASYPT_ENCRYPTOR_PASSWORD=<你的口令>`。`start-all.ps1` 会自动读取它；该文件已被 `.gitignore` 忽略，不会进仓库。
- 手动启动：先 `$env:JASYPT_ENCRYPTOR_PASSWORD="<你的口令>"`，再 `java -jar ...`。
- 服务器部署：用 systemd `Environment=`、Docker `-e`、或启动脚本 `export` 注入。

> ⚠️ 换了加密口令，需要重新生成所有密文（见 `代码健壮性走查报告.md` 修复说明）。口令丢了 → 密文解不开 → 服务起不来。

`salt-generator-classname` 用 `RandomSaltGenerator`（同一明文每次加密得到的密文不同），`algorithm` 为 `PBEWITHHMACSHA512ANDAES_256`。

### 7. AI 助教配置（v3 新增，可选）

AI 功能默认关闭（key 为空时前端显示"未配置"），接入步骤：

1. 注册智谱开放平台（bigmodel.cn），创建 API Key（GLM-4-Flash / GLM-4V-Flash 均为**免费模型**）；
2. 用项目同款 Jasypt 配置（PBEWITHHMACSHA512ANDAES_256）把 key 加密为 `ENC(...)`，替换 `schoolhelp-user/src/main/resources/application.yml` 中 `schoolhelp.ai.api-key` 的值（**仓库不落明文**）；
   - 也支持环境变量方式：`SCHOOLHELP_AI_API_KEY`（部署在服务器时经 `.env` 注入）。
3. 可选环境变量：`SCHOOLHELP_AI_BASE_URL`（默认智谱）、`SCHOOLHELP_AI_MODEL`（默认 glm-4-flash）、`SCHOOLHELP_AI_VISION_MODEL`（默认 glm-4v-flash）。

安全设计：key 只存服务端；问答接口走网关 JWT 鉴权（未登录不可用）；每用户每分钟限流 5 次；prompt 内置拒答敏感内容约束。

### 8. 冒烟脚本凭据（可选）

`deploy/smoke-verify.sh` / `deploy/restart-backend.sh` 的登录冒烟**不再内置密码**：凭据从环境变量 `ADMIN_USER` / `ADMIN_PASS` 或服务器 `/opt/schoolhelp/.env` 的 `ADMIN_PASS=` 行读取；未设置时该检查项自动跳过。

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

### 课表智能化（v3，重点）

- **作息配置**：每用户一套每节课起止时间（`schedule_time_config`），默认 12 节大学作息模板；弹窗内可设节数（4-12）、每节时长、课间间隔；改上课时间自动算下课时间，后续节次按课间顺延。
- **AI 作息建议**：一键按节数生成作息（大模型），非法自动回退默认模板。
- **周翻页与周数**：设置学期第一周日期后显示「第 N 周」并支持前后翻页；非视图周课程虚化（周次文本 + 单双周解析）。
- **调课**：拖拽课程到新格子 → 选「临时（仅本周，`schedule_change` 记录，可恢复）」或「永久（直接改条目）」。
- **同课多时段**：同一门课多次上课同色显示、详情聚合所有时段、编辑/删除可选联动范围。

### AI 助教与课表导入（v3，重点）

- 侧边浮动按钮 → 对话抽屉：多轮问答（Markdown 渲染）、本地历史、限流 5 次/分钟。
- 课表页「截图导入」：上传/拍照课表 → GLM-4V-Flash 识别为结构化条目 → 用户逐条核对 → 确认导入；可选同时申请录入课程库（同名去重）。
- **AI 结果永远经人工确认后才写库**。

### 校园日历（v3）

- 每日定时任务（03:10 + 启动时）同步：节假日（免费 API，国务院公布数据）+ 大模型生成考试/竞赛日历（失败回退内置知识库，AI 内容标注"以官方通知为准"）。
- 课表底部「大学活动」行 + PC 首页「本周校园日历」卡片；数据更新后登录弹提示。

### 七天免登录（v3）

网关校验 JWT 通过后，若剩余有效期不足 3 天则重签新 7 天 token 经 `X-Renewed-Token` 响应头下发，前端拦截器自动更新本地存储。只要 7 天内访问过一次，登录态一直有效。

### 设备自动分流

手机访问 PC 站自动跳移动端（网址加 `?pc=1` 强制留在 PC），PC 访问移动站同理（`?mobile=1`）。

---

## 六、部署说明

当前部署环境（演练用，非商业）：

- **开发机（本地）**：全部服务跑在 127.0.0.1，前后端分离。
- **线上（可选）**：一台云服务器 + Nginx（systemd 托管 4 个后端服务）。

> 服务器部署（目录约定、systemd、日志分级、一键脚本、依赖包清单）详见 **[`deploy/README.md`](deploy/README.md)**。
> 目录约定：后端 jar/logs/uploads 均下沉到 `/opt/schoolhelp/<服务>/`（`gateway|user|course|biz`）；PC 静态站 `/var/www/schoolhelp/pc/`（:80），移动端 `/var/www/schoolhelp/m/`（:8081）。

**Nginx 反代要点**（以 PC 端为例）：

```nginx
server {
    listen 80;
    server_name your-domain-or-ip;

    # 前端静态资源
    location / {
        root /var/www/schoolhelp/pc;
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
| AI 对话提示「AI 功能未配置」 | `schoolhelp.ai.api-key` 为空：按「四-7」配置智谱 API Key（ENC 密文或环境变量） |
| AI 课表识别报「AI 服务暂时不可用」 | 查 user 服务日志；智谱模型参数有上限（如 GLM-4V-Flash `max_tokens` ≤ 1024），调用参数超限会被 400 拒绝 |
| 课表不显示「第 N 周」/课程未虚化 | 未设置「学期第一周开始日期」（作息设置弹窗内），设置后生效 |
| 登录冒烟跳过（smoke-verify） | 冒烟凭据已外置：设置环境变量 `ADMIN_PASS` 或在服务器 `/opt/schoolhelp/.env` 加 `ADMIN_PASS=` 行 |

---

## 八、版本历史

| 版本 | 内容 |
|------|------|
| v1 | 账号/课表/课程库/作业/资料/评论/天气，双端前端 |
| v2 | 审批流（课程/作业/资料先审后展示）、班长审批、设备自动分流、密码强度 |
| v2.5 | 运维体系重构：systemd / 日志分级 / 一键部署脚本 / 配置加密（Jasypt）/ 冒烟自检 |
| **v3** | **品牌「课屿」；课表智能化（作息配置 + AI 作息建议 + 周翻页 + 周数/虚化 + 拖拽调课 + 连堂合并 + 同课同色）；AI 助教（对话 + 课表截图识别导入 + 批量入库申请）；校园日历（每日定时同步 + 登录更新提示 + 课表活动行）；七天免登录（JWT 滑动续期）；仓库安全脱敏（文档/脚本零明文密码）** |

---

## 九、说明

- 本项目的产品定位、需求与代码走查结论，见根目录另三份文档：
  - `开发日志.md`
  - `需求文档.md`（v3，含课表智能化 / AI 助教 / 校园日历需求）
  - `代码健壮性走查报告.md`
- **安全约定**：仓库内任何文件不出现明文密码/密钥（默认密码常量拆分存储、冒烟凭据外置、AI key 与数据库密码走 Jasypt `ENC(...)`）；发现残留请提交 issue。
- 仅供学习/校内小范围使用，请遵守学校规定与相关法律法规。
