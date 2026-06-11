# DBPJ-2026 校园活动报名系统

## 项目简介

这是一个数据库系统课程实践项目，面向学生、组织者和管理员三类角色，实现校园活动发布、审核、报名、候补、签到、反馈、信用分、站内通知、统计看板和智能查询等功能。

当前项目已经具备完整课程演示闭环：

- 后端：Spring Boot 4 + MyBatis-Plus + Spring JDBC。
- 前端：Vue 3 + Vite + TypeScript + Element Plus。
- 数据库：MySQL 8.4.8 LTS，`schema.sql` 提供新库一键初始化。
- 智能查询：OpenAI-compatible LLM + 受控逻辑视图 SQL 草稿 + 后端安全校验与权限注入。
- 测试：后端 MockMvc 接口契约、报名并发一致性、智能查询多角色用例；前端支持类型检查和生产构建。

## 当前完成度

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| 登录与鉴权 | 已完成 | 支持学生、组织者、管理员登录，接口通过 Bearer token 鉴权 |
| 活动管理 | 已完成 | 活动创建、编辑草稿、提交审核、审核通过/驳回、取消、列表和详情 |
| 报名候补 | 已完成 | 支持报名、满员候补、取消报名、候补自动转正和并发一致性控制 |
| 签到缺勤 | 已完成 | 学生获取签到码，组织者核销签到，活动结束后可标记缺勤 |
| 反馈评价 | 已完成 | 学生提交/更新评价，组织者和管理员查看反馈统计 |
| 信用分 | 已完成 | 签到加分、缺勤扣分、信用流水和风险学生概览 |
| 站内通知 | 已完成 | 报名、候补转正、签到、缺勤等事件写入通知 |
| 字典维护 | 已完成 | 管理员维护校区、场地、活动分类 |
| 统计看板 | 已完成 | 活动概览、校区使用、分类热度、反馈和信用统计 |
| 智能查询 | 基本完成 | 后端链路、权限边界和受控 SQL 已完成；真实模型稳定性和前端 E2E 仍需继续验收 |
| 文档 | 已完成 | README、数据库设计、前后端开发文档、SQL 初始化说明和阶段材料已整理 |

当前判断：系统已经达到课程项目提交和本地演示所需的主要功能完整度。后续工作主要集中在智能查询体验增强、真实模型样例验收、前端 E2E 和导出类锦上添花功能。

## 功能概览

### 学生端

- 浏览活动大厅、查看活动详情。
- 活动报名、满员候补、取消报名。
- 查看“我的活动”、获取签到码。
- 已签到活动可提交或更新反馈评价。
- 查看信用分和信用流水。
- 查看站内通知，包括报名成功、候补转正、签到成功、缺勤扣分等。
- 使用自然语言进行受控智能查询。

### 组织者端

- 创建活动、编辑草稿、提交审核。
- 管理本人创建的活动。
- 查看报名名单、核销签到码。
- 活动结束后标记缺勤。
- 查看活动反馈看板。
- 查询可申请场地和参与过自己活动的学生汇总。

### 管理员端

- 审核活动，通过或驳回。
- 维护校区、场地、活动分类字典。
- 查看基础统计、反馈概览、信用风险学生。
- 协助管理组织者活动。
- 使用管理员只读 SQL 草稿模式进行更自由的业务统计查询。

## 目录结构

```text
DBPJ-2026
├─ backend/activity                 # Spring Boot 后端项目
├─ frontend                         # Vue 前端项目
├─ docs                             # 需求、ER 图、数据库设计文档
├─ scripts                          # smoke test 脚本
├─ sql
│  ├─ schema.sql                    # 新库完整初始化入口
│  ├─ README.md                     # SQL 脚本用途说明
│  ├─ phase2_feedback.sql           # 旧库反馈功能增量脚本
│  ├─ phase2_credit.sql             # 旧库信用功能增量脚本
│  ├─ phase3_cascade_rules.sql      # 旧库级联规则增量脚本
│  ├─ phase3_query_indexes.sql      # 旧库查询索引增量脚本
│  ├─ migrate_password_hash.sql     # 旧库密码哈希迁移
│  ├─ fix_seed_utf8.sql             # 旧库 seed 中文修复
│  └─ performance_checks.sql        # EXPLAIN 检查脚本
├─ work_docs                        # 过程文档、接口文档和阶段材料
├─ docker-compose.yml               # MySQL 容器配置
└─ .env.example                     # 环境变量示例
```

## 环境要求

- JDK 25，或将 `backend/activity/pom.xml` 中的 `java.version` 调整为本机 JDK 支持版本。
- Maven 3.9+
- Node.js 18+
- npm
- Docker Desktop / Docker Engine

最近验证环境：

- Java 25.0.2
- Maven 3.9.9
- Spring Boot 4.0.6
- MySQL Docker 镜像 `mysql:8.4.8`

## 快速启动

复制环境变量：

```powershell
copy .env.example .env
```

启动数据库：

```powershell
docker compose up -d mysql
docker compose ps
```

启动后端：

```powershell
cd backend/activity
mvn spring-boot:run
```

启动前端：

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

访问地址：

```text
前端：http://localhost:5173
后端：http://localhost:8080
```

## 环境变量

常用配置：

| 配置项 | 说明 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 |
| `MYSQL_DATABASE` | 初始数据库名 |
| `MYSQL_USER` / `MYSQL_PASSWORD` | MySQL 普通用户 |
| `DB_URL` | 后端 JDBC 地址 |
| `DB_USERNAME` / `DB_PASSWORD` | 后端数据库账号 |
| `APP_AUTH_SECRET` | Token 签名密钥 |
| `APP_PASSWORD_HASH_ITERATIONS` | PBKDF2 迭代次数 |
| `LLM_ENABLED` | 是否启用模型查询规划 |
| `LLM_BASE_URL` | OpenAI-compatible API 地址 |
| `LLM_API_KEY` | 模型 API Key |
| `LLM_MODEL` | 模型名称 |
| `LLM_TIMEOUT_MS` | 模型调用超时时间 |
| `LLM_SQL_MODE` | 推荐为 `CONTROLLED_ALL` |
| `LLM_CONTROLLED_SQL_ENABLED` | 是否启用受控逻辑视图 SQL |
| `LLM_ADMIN_SQL_ENABLED` | 是否允许管理员 SQL 草稿模式 |

默认 `.env.example` 中 `LLM_ENABLED=false`。接入模型后建议：

```env
LLM_ENABLED=true
LLM_SQL_MODE=CONTROLLED_ALL
LLM_CONTROLLED_SQL_ENABLED=true
LLM_ADMIN_SQL_ENABLED=true
LLM_RESPONSE_FORMAT_ENABLED=false
LLM_REPAIR_ENABLED=false
LLM_SUMMARY_ENABLED=false
```

## 数据库初始化

首次启动时，MySQL 数据卷为空，Docker 会自动执行：

```text
sql/schema.sql
```

`schema.sql` 是当前唯一的新库主初始化脚本，已经整合建表、约束、索引、触发器、逻辑视图和初始测试数据。其他 `phase*.sql`、`migrate*.sql`、`fix*.sql` 仅用于旧数据库增量维护。

默认数据库连接信息：

```text
Host: localhost
Port: 3306
Database: campus_activity
Username: campus
Password: campus123
Root password: root123
```

完整重建数据库：

```powershell
docker compose down -v
docker compose up -d mysql
```

注意：普通的 `docker compose restart mysql` 不会重新执行初始化 SQL。只有删除 volume 后重新启动，MySQL 才会重新执行 `/docker-entrypoint-initdb.d` 下的脚本。

## 演示账号

初始化脚本内置账号密码均为：

```text
123456
```

| 角色 | 登录名 |
| --- | --- |
| 学生 | `20230001` |
| 组织者 | `计算机协会` 或 `13800000002` |
| 管理员 | `系统管理员` 或 `13800000003` |

密码字段以 PBKDF2 哈希形式保存，不保存明文。

## 后端运行

```powershell
cd backend/activity
mvn test
mvn spring-boot:run
```

打包运行：

```powershell
mvn -DskipTests package
java -jar target/activity-0.0.1-SNAPSHOT.jar
```

后端默认地址：

```text
http://localhost:8080
```

## 前端运行

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

生产构建：

```powershell
npm.cmd run build
```

如果 PowerShell 禁止执行 `npm.ps1`，优先使用 `npm.cmd`。

## 核心接口

所有业务接口默认以 `/api/v1` 为前缀。除登录外，请求需要携带：

```text
Authorization: Bearer <token>
```

| 模块 | 主要接口 |
| --- | --- |
| 认证 | `POST /auth/login`, `GET /auth/me` |
| 活动 | `GET /activities`, `GET /activities/{id}`, `POST /activities`, `PUT /activities/{id}`, `POST /activities/{id}/submit`, `POST /activities/{id}/review`, `POST /activities/{id}/cancel` |
| 报名 | `POST /activities/{id}/registrations`, `DELETE /registrations/{id}`, `GET /registrations/my`, `GET /activities/{id}/registrations` |
| 签到 | `GET /registrations/{id}/check-in-code`, `PATCH /registrations/check-in` |
| 缺勤 | `POST /activities/{id}/registrations/absences` |
| 反馈 | `POST /activities/{id}/feedback`, `GET /activities/{id}/feedback/my`, `GET /activities/{id}/feedback`, `GET /feedback/overview` |
| 信用 | `GET /credit/my`, `GET /credit/overview` |
| 通知 | `GET /notifications`, `GET /notifications/unread-count`, `PATCH /notifications/{id}/read`, `PATCH /notifications/read-all` |
| 字典 | `GET/POST /campuses`, `GET/POST /venues`, `GET/POST /categories` |
| 统计 | `GET /stats/overview`, `GET /stats/campus-usage`, `GET /stats/category-popularity` |
| 智能查询 | `POST /natural-query` |

统一响应格式：

```json
{
  "code": 20000,
  "message": "success",
  "data": {}
}
```

更完整的请求和响应示例见 [API 接口文档](./work_docs/API接口文档.md)。

## 智能查询

当前智能查询已经从固定规则模板升级为“模型优先、后端受控”的模式。

主流程：

1. 用户输入自然语言问题。
2. 后端调用 OpenAI-compatible 模型。
3. 模型输出 JSON，不直接执行数据库操作。
4. 普通用户优先输出基于逻辑视图的 `CONTROLLED_SQL`。
5. 后端校验只读 SELECT、逻辑视图白名单、字段白名单、敏感字段、分页上限。
6. 后端按当前角色注入权限条件，再编译为真实 SQL 执行。
7. 返回统一的动态表格协议：`summary + columns + rows + total`。

已支持的典型查询包括：

- “查询当前报名人数不为 0 的活动”
- “查询一个和数据库相关的活动”
- “查询全部活动，包括取消和过期的”
- “查询我的活动评价记录”
- “查询我评价过的活动中，在光华楼举办的，参与人数较多的活动”
- “查询容量至少 50 人的可申请活动场地”
- “查询参与过我创建活动的学生，按参与次数排序”
- “查询明天各场地活动占用情况”

边界说明：

- 当前自动化测试使用 Stub LLMClient 验证后端执行链路，不等同于真实云端模型稳定性验收。
- “可申请场地”目前主要覆盖容量、校区、场地等静态条件，按时间段判断空闲仍属于后续增强。
- 智能查询不是完全自由的数据库问答系统，所有查询都应落在受控视图、角色权限和只读 SQL 边界内。

相关说明：

- [智能查询实现说明](./work_docs/智能查询.md)
- [智能查询测试不完备说明](./work_docs/智能查询测试不完备说明.md)

## 数据库设计要点

核心表：

- `User`：用户表，按 `role` 区分学生、组织者、管理员。
- `Campus`、`Venue`、`Category`：校区、场地、分类字典。
- `Activity`：活动主表。
- `Registration`：报名、候补、取消、签到、缺勤记录。
- `ActivityFeedback`：活动反馈评价。
- `CreditRecord`：信用分流水。
- `Notification`：站内通知。

关键设计：

- `Registration(student_id, activity_id)` 唯一，避免重复报名。
- `ActivityFeedback(registration_id)` 唯一，保证一条报名最多一条评价。
- `CreditRecord(reason_type, registration_id)` 唯一，避免签到或缺勤信用流水重复写入。
- 场地时间冲突、活动容量、用户角色合法性、反馈一致性、信用流水一致性由触发器兜底。
- `Campus -> Venue -> Activity -> Registration/ActivityFeedback` 使用级联删除，避免字典删除后残留孤立业务数据。
- 通知、活动列表、报名名单、反馈统计、信用流水等常用查询路径已建立组合索引。

完整说明见：

- [数据库设计文档](./docs/数据库设计文档.md)
- [数据库设计实验文档](./docs/数据库设计实验文档.md)
- [SQL 初始化说明](./sql/README.md)

## 测试与质量验证

后端测试依赖 MySQL。运行前请先确认 Docker Desktop 或 Docker Engine 已启动，并且数据库容器处于运行状态：

```powershell
docker compose up -d mysql
docker compose ps
cd backend/activity
mvn test
```

最近一次完整通过记录：

```text
Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

当前覆盖：

- 应用上下文启动。
- PBKDF2 登录校验。
- MockMvc 登录、鉴权失败、跨角色拒绝、活动列表、报名、签到、通知接口契约。
- 报名满员候补。
- 正选取消后候补第一位自动转正。
- 并发报名不超卖，候补序号连续且不重复。
- 签到幂等，信用流水不重复。
- 反馈创建和更新。
- 缺勤扣分只写入一次。
- 活动状态、权限和截止时间校验。
- 智能查询契约、歧义追问、受控逻辑视图 SQL、管理员 SQL 草稿、敏感字段拦截、多角色多维查询。

前端类型检查和生产构建：

```powershell
cd frontend
npm.cmd run build
```

API smoke test：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1 -Port 18080
```

数据库执行计划检查：

```powershell
docker cp sql/performance_checks.sql dbpj-2026-mysql:/tmp/performance_checks.sql
docker exec dbpj-2026-mysql mysql -ucampus -pcampus123 campus_activity -e "source /tmp/performance_checks.sql"
```

本机最近检查说明，时间为 2026-06-11：

- `mvn test` 已执行，但当前机器 Docker daemon 未运行，测试因 MySQL `Connection refused` 失败。
- `npm.cmd run build` 已通过 `vue-tsc --noEmit`，进入 Vite 输出阶段后因写入 `frontend/dist/assets` 被系统拒绝而失败，未发现 TypeScript 类型错误。

## 常见问题

### 数据库没有应用最新结构

MySQL 官方镜像只会在数据目录为空时执行初始化脚本。请重建 volume：

```powershell
docker compose down -v
docker compose up -d mysql
```

### 数据库连接失败

先确认 Docker daemon 和 MySQL 容器状态：

```powershell
docker compose ps
docker compose up -d mysql
```

再确认 `.env` 中账号密码和 `DB_URL` 是否正确。

### PowerShell 无法运行 npm

如果出现 `npm.ps1 cannot be loaded`，使用：

```powershell
npm.cmd install
npm.cmd run dev
npm.cmd run build
```

### Vite 构建无法写入 dist

如果出现 `EPERM: operation not permitted, mkdir 'frontend\dist\assets'`，通常是 `dist` 目录被编辑器、预览服务或系统权限占用。关闭占用进程后删除或清空 `frontend/dist`，再重新执行：

```powershell
npm.cmd run build
```

### 智能查询模型超时

可适当调大：

```env
LLM_TIMEOUT_MS=30000
```

如果模型不稳定，先将：

```env
LLM_ENABLED=false
```

系统会只走受限的规则/DSL 兜底路径，但复杂查询体验会下降。

## 文档索引

- [需求文档](./docs/需求文档.md)
- [系统设计方案](./work_docs/系统设计方案.md)
- [数据库设计文档](./docs/数据库设计文档.md)
- [数据库设计实验文档](./docs/数据库设计实验文档.md)
- [API 接口文档](./work_docs/API接口文档.md)
- [后端开发文档](./backend/docs/后端开发文档.md)
- [前端开发文档](./frontend/docs/前端开发文档.md)
- [SQL 初始化说明](./sql/README.md)
- [智能查询实现说明](./work_docs/智能查询.md)
- [智能查询测试不完备说明](./work_docs/智能查询测试不完备说明.md)
- [阶段性报告](./work_docs/阶段性报告.md)
- [开发迭代计划](./work_docs/开发迭代计划.md)

## 后续方向

建议后续优先推进：

1. 用真实模型样例集验收智能查询稳定性，记录模型输出、后端执行结果和失败原因。
2. 增强场地可申请查询，支持按日期和时间段判断场地占用冲突。
3. 补充信用、通知领域的智能查询深度测试。
4. 增加 SQL 字面值参数化，进一步收紧受控 SQL 安全边界。
5. 完成前端智能查询页面 E2E 验证。
6. 补充报名名单导出能力，例如 CSV 或 Excel。
7. 评估 MySQL FULLTEXT 或 embedding 检索，提升“相关活动”语义搜索体验。
