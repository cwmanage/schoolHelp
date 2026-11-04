# 课屿 Keyu（schoolHelp 校园助手）

> 哈尔滨学院专属校园助手 —— 课表、课程库、作业提醒、课程资料、课程评论、**AI 智能体助教**一站通。
> 品牌名「课屿」：课程如屿、学海同航；仓库名/包名/服务名沿用 schoolHelp。
> 单人/小团队可跑起来的 Spring Cloud 微服务练手项目（研一练手，非商业产品）。

---

## 一、这是什么

一个给同班/同专业同学用的小工具，核心能力：

1. **我的课表（v3 大幅增强）**：可视化周课表；每节课显示起止时间（作息配置 + **AI 生成作息建议**）；当前第几周、周翻页查看任意周、非本周课程虚化；连堂课合并展示、同课程多时段同色；**拖拽调课**（临时=本周 / 永久=本学期）；底部「大学活动」行展示当天考试/竞赛/节假日。
2. **课程库 + 作业**：统一的课程信息、作业截止时间；临期作业自动在首页红色条幅滚动提醒。
3. **资料共享 + 评论**：课程资料上传下载、课程评论区（支持匿名，管理员可追溯真实身份）。
4. **AI 智能体助教（v4 升级）**：接入智谱大模型——**五层架构的 Agent**（手写 function-calling 循环，无框架）：可自主调用课表/作息/天气等工具组合推理（如"明天第一节课几点"=查课表×查作息双工具接力）；**多轮记忆持久化**（MySQL 按 userId+sessionId 存储，跨设备）；**模型降级链**（多 Key 多端点自动熔断切换，高峰可用）；课表截图拍照识别批量导入（视觉降级链）。
5. **校园日历（v3）**：每日定时任务同步节假日（免费 API）+ AI 生成考试/竞赛日历；登录后有更新弹提示；课表与首页展示。
6. **七天免登录（v3）**：JWT 滑动续期——7 天内有任意访问即保持登录。
7. **HTTPS 上线（v4）**：域名 `https://cwproject.xyz`（Let's Encrypt 证书自动续期）；HTTP 308 跳转 HTTPS（保留 POST 方法）；同域名 UA 设备分流（手机/PC 同一网址返回对应版本页面，无跳转）。

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
| 数据库 | MySQL 5.7+（服务器 5.7 / 本地 8 均可） | 库名 `schoolhelpdb`，字符集 utf8mb4 |
| 认证 | JWT（Hutool 签发）+ BCrypt | 无状态鉴权，密码强度四档 |
| 前端 | Vue 3 + Vite + Element Plus + Pinia + Vue Router + axios | 双端：移动端 + PC 端 |
| **Agent/LLM** | 智谱开放平台 OpenAI 兼容接口（多模型多 Key） | 手写 function-calling 循环（无框架）；五层架构见「五」；端点降级链 + 熔断 |
| 校园日历 | timor.tech 节假日免费 API + 大模型生成考试/竞赛日历 | 服务端每日定时任务同步（03:10 + 启动时） |
| 天气 | Open-Meteo 免费 API + ip-api 定位 | 默认哈尔滨学院；用户 IP 距学院 >30km 自动切换为用户位置 |
| 线上接入 | Nginx（HTTPS/Let's Encrypt）+ UA 分流 | 308 保留方法跳转；证书自动续期 |

---

## 三、工程结构

```
schoolHelp/
├── pom.xml                      # 父 POM（聚合 5 模块，统一依赖版本）
├── start-all.ps1                # 本地一键启动 4 个后端服务
├── sql/                         # v1/v2 建库脚本（服务器另有 deploy/sql/ 全量迁移）
├── deploy/                      # 部署资产：nginx 配置 / 冒烟脚本 / 全量 SQL / 交接文档
├── schoolhelp-common/           # 公共模块：Result / 异常 / 常量 / JWT / 用户上下文
├── schoolhelp-gateway/          # 网关（本地 :8080 / 服务器 :8085，唯一对外入口）
├── schoolhelp-user/             # 用户 + 课表 + AI 智能体 :8101
│   └── src/main/java/.../agent/ # ★ 五层 Agent 架构（详见「五」）
│       ├── AgentChatService     #     编排器（决策循环 + 精炼 + 记忆 + 时间预算）
│       ├── ModelPool            #     路由层（端点降级链 + 熔断 + 过期踢出）
│       ├── LlmClient            #     适配层（thinking 分级 + SSE 契约 + 审计出口）
│       ├── ContextManager       #     上下文层（窗口裁剪 + 压缩钩子）
│       ├── AgentToolRegistry    #     工具层（注解扫描 + Schema 生成）
│       ├── ChatMemoryService    #     记忆持久化（MySQL）
│       └── tools/               #     工具实现（课表/作息/天气，@AgentTool 注册）
├── schoolhelp-course/           # 课程 + 作业 + 资料服务 :8102
├── schoolhelp-biz/              # 业务聚合服务 :8103（临期作业、评论、提交标记）
├── schoolhelp-activity/         # 大学活动分析服务 :8104（v5 拆分：校园日历同步/查询/已读）
├── schoolhelp-web/              # 移动端前端（Vite dev :5173）
└── schoolhelp-web-pc/           # PC 端前端（Vite dev :5174）
```

**5 个后端模块说明**

| 模块 | 本地端口 | 服务器端口 | 职责 |
|------|------|------|------|
| common | — | — | 被其他模块依赖：`Result` 统一响应、`BusinessException`、`GlobalExceptionHandler`、`CommonConstants`、`JwtUtil`、`UserContext`、`ReviewDTO` |
| gateway | 8080 | **8085** | 路由转发、JWT 校验（`AuthGlobalFilter`，order=-100）、白名单、CORS；对外只暴露这一个端口 |
| user | 8101 | 8101 | 注册/登录/用户资料/密码修改、个人课表 CRUD、**作息配置 / 学期设置 / 调课 / 校园日历 / AI 智能体** |
| course | 8102 | 8102 | 课程库、作业、课程资料（含审批状态机） |
| biz | 8103 | 8103 | 临期作业聚合（Feign 调 user+course）、课程评论、作业提交标记 |
| activity | 8104 | 8104 | **大学活动分析（v5）**：校园日历同步（节假日 API + AI 生成考试/竞赛）、事件查询、已读状态；高内聚独立微服务 |

> gateway 通过 `StripPrefix=2` 去掉 `/api/<svc>` 两段前缀后转发；例如 `POST /api/user/auth/login` → `user` 服务 `/auth/login`。**nginx 反代不要提前剥 `/api`**（gateway 依赖完整路径做路由匹配）。

---

## 四、快速开始

### 0. 前置条件

- JDK 17（本项目用 `C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot`）
- Maven（可用自带 `mvnw`）
- MySQL 5.7+（本地 3306）
- Nacos 2.x（本地 8848）
- Node.js 22 LTS（前端，要求 `^20.19.0 || >=22.12.0`；低于此版本 `vite build` 会报 `does not provide an export named 'styleText'`）
- 环境变量 `JASYPT_ENCRYPTOR_PASSWORD`（解密配置文件里的 `ENC(...)` 密文，详见「6. 配置加密」）

### 1. 初始化数据库

```sql
CREATE DATABASE schoolhelpdb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

然后依次执行 `sql/` 与 `deploy/sql/` 下的迁移脚本（v1 建表 → v2 审批流 → v3 班长审批 → v4 作息配置 → v5 课表升级 → v6/v7 Agent 记忆表）。

> Agent 记忆表最终结构见 `deploy/sql/v7_agent_memory_v2.sql`（`agent_chat_message`：user_id + session_id + role + content + tokens_used；v7 为重建脚本，含测试数据时勿在生产直接执行）。

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

### 7. AI 智能体配置（v4）

AI 功能默认关闭（key 为空时前端显示"未配置"），接入步骤：

1. 注册智谱开放平台（bigmodel.cn），实名后创建 API Key（GLM-4.7-Flash / GLM-4.5-Flash / GLM-4-Flash / GLM-4.6V-Flash 均为**免费模型**，各账号每模型 1 并发）；
2. 把 key 经环境变量注入（**仓库不落明文**）：本地 `.env.local` / 服务器 `.env`（600 权限）写 `SCHOOLHELP_AI_API_KEY=<明文>`；
3. 可选环境变量：`SCHOOLHELP_TRIAL_API_KEY`（第二个 Key——其免费模型与资源包入降级池）、`SCHOOLHELP_AI_BASE_URL`、`SCHOOLHELP_AI_MODEL`（普通问答）、`SCHOOLHELP_AI_VISION_MODEL`。

**模型池**在 `application.yml` 的 `schoolhelp.ai.profiles` 声明（11 端点）：每个端点一张 ModelProfile（key-ref / model / context-tokens / tier=FLAGSHIP|LITE|VISION / thinking / vision / expire-at）。资源包端点带 `expire-at` 到期自动踢出。

**降级链**（端点顺序 = yml 声明顺序）：资源包优先（先过期先用）→ 免费/低档兜底；任一端点限流（429/1305/1302/1113）→ 熔断 5 分钟 + 切下一个端点。

安全设计：key 只存服务端；所有 AI 接口走网关 JWT 鉴权；每用户每分钟限流 5 次（Agent 独立计数）；prompt 内置拒答敏感内容约束；**所有模型输出经 LlmClient 统一出口**（内容审计插入点）。

### 8. 冒烟脚本凭据（可选）

`deploy/smoke-verify.sh` / `deploy/restart-backend.sh` 的登录冒烟**不再内置密码**：凭据从环境变量 `ADMIN_USER` / `ADMIN_PASS` 或服务器 `/opt/schoolhelp/.env` 的 `ADMIN_PASS=` 行读取；未设置时该检查项自动跳过。

---

## 五、Agent 智能体架构（v4 核心，重点）

手写 function-calling 循环（**无框架**），五层分离——每层只管一件事，未来复杂化（多级流水线/MCP/上下文压缩）都有落点：

```
编排层  AgentChatService     步骤策略（决策循环/精炼开关/150s 时间预算）+ 记忆读写
上下文层 ContextManager      按目标模型窗口裁剪（200K/128K/16K 异构适配）+ 压缩钩子（预留）
路由层  ModelPool            端点降级链 + 熔断(5min) + Semaphore(1) 并发隔离 + 过期踢出
适配层  LlmClient            thinking 分级组装 + SSE 契约（降级决策在首 token 前）+ 审计出口
工具层  AgentToolRegistry    @AgentTool 注解扫描 → JSON Schema 生成 → 反射执行
```

**工作流程**：`messages = [system(含北京时间), ...记忆(最近8条), user]` → 按降级链调 LLM → 返回 `tool_calls` 则执行工具回填继续 → 返回 `content` 即最终答案。

**工具**（`@AgentTool` 注解声明，新增工具零改核心代码）：`get_my_schedule`（课表，带中文星期名）`get_my_time_config`（作息配置）`get_weather`（天气）。**写操作工具预留 `write=true` 属性**（确认机制后续做）。

**记忆**：`agent_chat_message` 表按 `user_id + session_id` 持久化（跨设备、多会话预留），每轮回放最近 8 条，`tokens_used` 记录消耗。

**降级链**（端点顺序 = yml profiles 声明顺序）：资源包优先（先过期先用，`expire-at` 到期自动踢出）→ 免费模型兜底；任一端点限流（429/1305/1302/1113/非JSON错误体）→ 熔断 5 分钟 + 切下一个端点；读超时不熔断（防误伤好端点）。

**新接口**：

| 接口 | 说明 |
|------|------|
| `POST /api/user/ai/agent/chat` | 智能体对话（body `{message}`；AI 助教前端已全量切换），返回 `{answer, rounds, usedModel, toolTrace, tokensUsed}` |
| `DELETE /api/user/ai/agent/history` | 清空当前用户记忆（前端"清空对话"按钮） |
| `POST /api/user/ai/schedule-ocr` | 课表截图识别（视觉降级链），返回 `{items, usedModel, degradeTrace}`——降级时前端弹提示 |

普通问答旧接口 `/ai/chat` 保留（兼容）。

---

## 六、核心业务流程

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

### AI 助教与课表导入

- 侧边浮动按钮 → 对话抽屉：**智能体对话**（自动调工具：课表/作息/天气组合推理，如"明天第一节课几点"=课表×作息双工具接力）、Markdown 渲染、限流 5 次/分钟、记忆持久化。
- 课表页「截图导入」：上传/拍照课表 → 视觉降级链识别为结构化条目（降级自动切换并前端提示）→ 用户逐条核对 → 确认导入；可选同时申请录入课程库（同名去重）。
- **AI 结果永远经人工确认后才写库**；回答中的日期对齐由「中文星期名 + 日期纪律提示词」保证（工具返回带 `weekdayName`，杜绝数字换算错日）。

### 校园日历（v3）

- 每日定时任务（03:10 + 启动时）同步：节假日（免费 API，国务院公布数据）+ 大模型生成考试/竞赛日历（失败回退内置知识库，AI 内容标注"以官方通知为准"）。
- 课表底部「大学活动」行 + PC 首页「本周校园日历」卡片；数据更新后登录弹提示。

### 七天免登录（v3）

网关校验 JWT 通过后，若剩余有效期不足 3 天则重签新 7 天 token 经 `X-Renewed-Token` 响应头下发，前端拦截器自动更新本地存储。只要 7 天内访问过一次，登录态一直有效。

### HTTPS 与设备分流（v4）

- 域名 `cwproject.xyz` / `www.cwproject.xyz` → HTTPS（Let's Encrypt，certbot 自动续期）；HTTP 一律 **308** 跳转 HTTPS（保留请求方法，POST 登录不退化）。
- **同域 UA 分流**：手机与电脑访问**同一网址**，nginx 按 User-Agent 返回对应版本页面（无跳转）；强制切换：`?pc=1` / `?mobile=1`。
- nginx 反代要点：`proxy_pass` **不带尾斜杠**（`/api` 前缀必须留给 gateway 的 StripPrefix=2）；**禁止设置 `Connection: upgrade`**（无 WebSocket 需求，Reactor Netty 会 404）；`proxy_read_timeout 180s`（Agent 长循环）。

---

## 七、部署说明

当前线上：**腾讯云服务器（62.234.29.217）+ 域名 cwproject.xyz（HTTPS）**，systemd 托管。

| 组件 | 线上形态 |
|------|---------|
| Nginx | :80（308→HTTPS）/ :443（TLS）+ 同域 UA 分流 |
| gateway | :8085（systemd `schoolhelp-gateway`，`TZ=Asia/Shanghai`） |
| user / course / biz / activity | :8101 / :8102 / :8103 / **:8104**（systemd，日志 `/opt/schoolhelp/<svc>/logs/`） |
| Nacos | :8848 |
| MySQL 5.7 | :3306（**仅本机监听，不对公网**；远程调试用客户端的 SSH 隧道功能） |
| 前端 | `/var/www/schoolhelp/pc/`（PC）与 `/var/www/schoolhelp/m/`（移动），同域 UA 分流 |
| 冒烟 | `sudo bash /opt/schoolhelp/smoke-verify.sh`（28 项，登录走 nginx HTTPS 全链路） |

> 服务器部署（目录约定、systemd、日志分级、一键脚本、依赖包清单）详见 **[`deploy/README.md`](deploy/README.md)**。
> 服务器环境变量（`/opt/schoolhelp/.env`，600 权限）：`JASYPT_ENCRYPTOR_PASSWORD` / `ADMIN_PASS` / `SCHOOLHELP_TRIAL_API_KEY`（第二把 Key）等。
> 本地开发：全部服务跑在 127.0.0.1（gateway 8080），前后端分离。

---

## 八、常见问题（排障备忘）

| 现象 | 原因/解决 |
|------|-----------|
| 网关 503 / 找不到服务 | 服务未注册到 Nacos。检查各服务启动日志、Nacos 服务列表 |
| 接口 401 | 没带 `Authorization: Bearer <token>`，或 token 过期 |
| AI 对话「免费模型高峰期拥挤」 | 免费档为全平台共享池，高峰（20-22 点）可能触发 1305/1302；系统已自动降级链切换（资源包/备模型），持续失败等几分钟再试 |
| AI 回答日期/星期不对 | 已内置三层防护（工具返回中文星期名 / system 日期纪律 / 历史时间标注）；若复现请查 user 日志 `LLM[...]` 与 `工具` 轨迹 |
| nginx 504（AI 接口） | Agent 长循环超 nginx 超时——已加端点 45s 超时 + deadline 150s；若仍出现检查 nginx `proxy_read_timeout 180s` 是否生效 |
| 改了 application.yml 不生效 | **yml 打在 jar 内**：必须 `mvn package` 重打包再重启（pull+restart 不生效）；验证：`unzip -p jar BOOT-INF/classes/application.yml \| grep <关键字>` |
| nginx 404 但 jar 正常 | 检查 `proxy_pass` 是否误带尾斜杠（会提前剥 `/api` 前缀导致 gateway 路由 404）；确认无 `Connection: upgrade` 头 |
| 数据库中文变 `????` | 写入源头编码错误导致脏数据。**用 `SELECT HEX(字段)` 查库确认**，别信控制台乱码 |
| 启动报 `Unable to decrypt` | 没设 `JASYPT_ENCRYPTOR_PASSWORD`，或口令与加密时不一致。检查 `.env.local` / 环境变量 |
| 登录冒烟跳过（smoke-verify） | 冒烟凭据已外置：设置环境变量 `ADMIN_PASS` 或在服务器 `/opt/schoolhelp/.env` 加 `ADMIN_PASS=` 行 |

---

## 九、版本历史

| 版本 | 内容 |
|------|------|
| v1 | 账号/课表/课程库/作业/资料/评论/天气，双端前端 |
| v2 | 审批流（课程/作业/资料先审后展示）、班长审批、设备自动分流、密码强度 |
| v2.5 | 运维体系重构：systemd / 日志分级 / 一键部署脚本 / 配置加密（Jasypt）/ 冒烟自检 |
| v3 | 品牌「课屿」；课表智能化；AI 助教（对话 + 课表截图识别）；校园日历；七天免登录；仓库安全脱敏 |
| **v5** | **后端微服务拆分：新建 schoolhelp-activity 服务（校园日历全量迁出 user，高内聚低耦合）；网关路由 /api/activity/**；移动端底标 4→5（+大学活动分析）；前端 API 路径迁移** |
| **v4** | **域名 + HTTPS 上线（cwproject.xyz，Let's Encrypt 自动续期，308 保留方法跳转）；同域 UA 设备分流（无跳转）；AI 智能体（手写 function-calling 五层架构：工具调用/记忆持久化/多 Key 模型降级链+熔断/任务分级编排/时间预算）；AI 助教前端全量切换智能体接口；视觉识别入降级池；服务器迁移与 DGRADER 清理（单应用专属服务器）** |

---

## 十、说明

- 本项目的产品定位、需求与代码走查结论，见根目录文档：`开发日志.md`、`需求文档.md`、`代码健壮性走查报告.md`；部署细节见 `deploy/README.md`。
- **安全约定**：仓库内任何文件不出现明文密码/密钥（默认密码常量拆分存储、冒烟凭据外置、AI key 与数据库密码走 Jasypt `ENC(...)` 或环境变量注入）；发现残留请提交 issue。
- 仅供学习/校内小范围使用，请遵守学校规定与相关法律法规。
