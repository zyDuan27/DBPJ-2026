# DBPJ-2026 校园活动报名系统

## 项目简介

本项目是数据库系统课程实践项目，目标是实现一个以 MySQL 为核心数据层的校园活动报名 Web 系统。系统面向学生、组织者、管理员三类角色，覆盖活动发布审核、活动报名、候补递补、现场签到、反馈评价、信用分、统计看板和站内通知等完整流程。

当前版本已经具备较完整的演示闭环：后端采用 Spring Boot + MyBatis-Plus，前端采用 Vue 3 + Vite + Element Plus，数据库脚本包含建表、约束、索引、触发器和初始化测试数据。

## 功能概览

### 学生端

- 浏览活动大厅、查看活动详情。
- 活动报名、满员进入候补、取消报名。
- 查看“我的活动”、生成签到码。
- 已签到活动可提交或更新反馈评价。
- 查看信用分和信用流水。
- 查看站内通知，包括报名成功、候补转正、签到成功、缺勤记录等。

### 组织者端

- 创建活动、编辑草稿、提交审核。
- 管理本人活动列表。
- 查看报名名单、核销签到码。
- 活动结束后标记缺勤。
- 查看活动反馈看板。
- 接收审核结果通知。

### 管理员端

- 审核活动，通过或驳回。
- 维护校区、场地、分类字典。
- 查看基础统计、反馈概览和信用风险学生。
- 可进入组织者活动管理视图协助处理活动。

### 数据库与工程能力

- 主外键、唯一性约束、检查约束、级联规则。
- 活动时间冲突、评价一致性、信用流水一致性的触发器校验。
- 报名人数 `current_enrollment` 由后端事务维护，数据库约束兜底。
- 组合索引覆盖活动列表、报名名单、候补队列、反馈统计、信用统计、通知列表。
- MockMvc 接口契约测试和报名并发一致性测试。

## 技术栈

| 层级 | 技术 |
| :--- | :--- |
| 前端 | Vue 3、Vite、TypeScript、Element Plus、Pinia、Vue Router、Axios |
| 后端 | Spring Boot 4、Spring WebMVC、MyBatis-Plus、Spring JDBC、HikariCP、Bean Validation |
| 数据库 | MySQL 8.4.8 LTS |
| 测试 | JUnit 5、Spring Boot Test、MockMvc |
| 部署辅助 | Docker Compose |

## 目录结构

```text
DBPJ-2026
├─ backend
│  ├─ activity              # Spring Boot 后端项目
│  └─ docs                  # 后端开发文档
├─ frontend                 # Vue 前端项目
├─ docs                     # 需求、ER 图、数据库设计文档
├─ scripts                  # smoke test 脚本
├─ sql
│  ├─ schema.sql            # 建表、约束、索引、触发器和初始化数据
│  ├─ phase2_feedback.sql   # 反馈功能增量脚本
│  ├─ phase2_credit.sql     # 信用分功能增量脚本
│  └─ fix_seed_utf8.sql     # 旧容器中文 seed 修复脚本
├─ work_docs                # 过程文档和迭代计划
└─ docker-compose.yml       # MySQL 容器配置
```

后端核心包结构：

```text
com.campus.activity
├─ common        # 统一响应、异常、鉴权上下文、枚举
├─ controller    # REST API 入口
├─ service       # 业务规则、权限校验、事务和状态流转
└─ model
   ├─ dto        # 请求参数对象
   ├─ entity     # 数据库实体
   ├─ mapper     # MyBatis-Plus Mapper 和 SQL
   ├─ row        # 查询投影
   └─ vo         # 接口响应对象
```

## 环境要求

- JDK 25，或将 `backend/activity/pom.xml` 中的 `java.version` 改为本机 JDK 支持版本。
- Maven 3.9+
- Node.js 18+
- npm
- Docker Desktop / Docker Engine

项目最近验证环境：

- Java 25.0.2
- Spring Boot 4.0.6
- MySQL Docker 镜像 `mysql:8.4.8`

## 数据库启动

在项目根目录执行：

```bash
docker compose up -d mysql
```

首次启动会自动执行 [sql/schema.sql](./sql/schema.sql)，创建数据库 `campus_activity`、核心表、约束、索引、触发器和初始化演示数据。

数据库连接信息：

```text
Host: localhost
Port: 3306
Database: campus_activity
Username: campus
Password: campus123
Root password: root123
```

查看容器状态：

```bash
docker compose ps
```

完全重建数据库：

```bash
docker compose down -v
docker compose up -d mysql
```

旧容器如果已经初始化过，不会自动重新执行新的 `schema.sql`。需要应用最新表结构时，推荐重建 volume；若只是修复旧 seed 中文乱码，可执行：

```bash
docker cp sql/fix_seed_utf8.sql dbpj-2026-mysql:/tmp/fix_seed_utf8.sql
docker exec dbpj-2026-mysql sh -c "mysql --default-character-set=utf8mb4 -ucampus -pcampus123 -D campus_activity < /tmp/fix_seed_utf8.sql"
```

## 后端运行

进入后端目录：

```bash
cd backend/activity
```

运行测试：

```bash
mvn test
```

打包：

```bash
mvn -DskipTests package
```

启动：

```bash
java -jar target/activity-0.0.1-SNAPSHOT.jar
```

后端默认地址：

```text
http://localhost:8080
```

后端配置文件位于 [application.yml](./backend/activity/src/main/resources/application.yml)。支持通过环境变量覆盖敏感配置：

```text
DB_URL
DB_USERNAME
DB_PASSWORD
APP_AUTH_SECRET
APP_PASSWORD_HASH_ITERATIONS
```

## 前端运行

进入前端目录：

```bash
cd frontend
```

安装依赖：

```bash
npm install
```

启动开发服务：

```bash
npm run dev
```

如果 PowerShell 禁止执行 `npm.ps1`，使用：

```bash
npm.cmd run dev
```

前端默认地址：

```text
http://localhost:5173
```

生产构建：

```bash
npm.cmd run build
```

## 演示账号

初始化脚本内置账号密码均为：

```text
123456
```

| 角色 | 登录名 |
| :--- | :--- |
| 学生 | `20230001` |
| 组织者 | `计算机协会` 或 `13800000002` |
| 管理员 | `系统管理员` 或 `13800000003` |

密码字段以 PBKDF2 哈希形式保存，不保存明文。

## 核心接口概览

所有业务接口默认以 `/api/v1` 为前缀。除登录外，接口需要请求头：

```text
Authorization: Bearer <token>
```

| 模块 | 主要接口 |
| :--- | :--- |
| 认证 | `POST /auth/login`、`GET /auth/me` |
| 活动 | `GET /activities`、`GET /activities/{id}`、`POST /activities`、`PUT /activities/{id}`、`POST /activities/{id}/submit`、`POST /activities/{id}/review`、`POST /activities/{id}/cancel` |
| 报名 | `POST /activities/{id}/registrations`、`DELETE /registrations/{id}`、`GET /registrations/my`、`GET /activities/{id}/registrations` |
| 签到 | `GET /registrations/{id}/check-in-code`、`PATCH /registrations/check-in` |
| 缺勤 | `POST /activities/{id}/registrations/absences` |
| 反馈 | `POST /activities/{id}/feedback`、`GET /activities/{id}/feedback/mine`、`GET /activities/{id}/feedback/board` |
| 信用 | `GET /credits/my`、`GET /credits/overview` |
| 统计 | `GET /stats/overview` 等 |
| 通知 | `GET /notifications`、`GET /notifications/unread-count`、`PATCH /notifications/{id}/read`、`PATCH /notifications/read-all` |
| 智能查询 | `POST /natural-query` |

统一响应格式：

```json
{
  "code": 20000,
  "message": "success",
  "data": {}
}
```

## 数据库设计要点

当前核心表：

- `User`：用户表，按 `role` 区分学生、组织者、管理员。
- `Campus`、`Venue`、`Category`：校区、场地、分类字典。
- `Activity`：活动主表，包含状态、时间、容量、组织者、审核人。
- `Registration`：报名记录，包含正式报名、候补、取消、已签到、缺勤状态。
- `ActivityFeedback`：活动反馈。
- `CreditRecord`：信用分流水。
- `Notification`：站内通知。

关键设计：

- `Registration(student_id, activity_id)` 唯一，避免重复报名。
- `ActivityFeedback(registration_id)` 唯一，保证一条报名最多一条评价。
- `CreditRecord(reason_type, registration_id)` 唯一，避免签到或缺勤信用流水重复写入。
- `Notification(recipient_id, is_read, created_at)` 支撑未读通知和分页查询。
- 场地时间冲突、活动容量、用户角色合法性、反馈/信用流水一致性由数据库触发器兜底。

更完整说明见 [数据库设计实验文档](./docs/数据库设计实验文档.md)。

## 测试与质量验证

后端集成测试：

```bash
cd backend/activity
mvn test
```

当前覆盖：

- 应用上下文启动。
- PBKDF2 登录校验。
- MockMvc 登录、鉴权失败、跨角色拒绝、活动列表、报名、签到错误码、通知接口契约。
- 报名满员进入候补。
- 正选取消后候补第一位自动转正。
- 并发报名不超卖，候补序号连续且不重复。
- 签到幂等，信用流水不重复。
- 反馈可更新。
- 缺勤扣分只写入一次。
- 活动状态、权限和截止时间校验。

最近一次验证结果：

```text
Tests run: 25, Failures: 0, Errors: 0
BUILD SUCCESS
```

前端构建验证：

```bash
cd frontend
npm.cmd run build
```

最近一次验证结果：构建通过。Vite 对 chunk 体积有提示，但不影响当前构建产物。

API smoke test：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1 -Port 18080
```

## 常见问题

### 数据库连接失败

先确认 MySQL 容器是否启动：

```bash
docker compose ps
docker compose up -d mysql
```

确认后端配置中的账号密码：

```text
username: campus
password: campus123
```

### PowerShell 无法运行 npm

如果出现 `npm.ps1 cannot be loaded`，使用：

```bash
npm.cmd install
npm.cmd run dev
npm.cmd run build
```

### 旧数据库缺少新表

Docker volume 已经初始化后，MySQL 不会再次执行 `docker-entrypoint-initdb.d` 里的脚本。若缺少 `Notification` 等新表，推荐：

```bash
docker compose down -v
docker compose up -d mysql
```

### 中文乱码

项目已在以下位置固定 `utf8mb4`：

- `sql/schema.sql`
- `docker-compose.yml`
- `backend/activity/src/main/resources/application.yml`

旧数据乱码时可执行 `sql/fix_seed_utf8.sql`，或重建数据库 volume。

## 文档索引

- [需求文档](./docs/需求文档.md)
- [数据库设计文档](./docs/数据库设计文档.md)
- [数据库设计实验文档](./docs/数据库设计实验文档.md)
- [ER 图](./docs/ER图.png)
- [后端开发文档](./backend/docs/后端开发文档.md)
- [前端开发文档](./frontend/docs/前端开发文档.md)
- [开发迭代计划](./work_docs/开发迭代计划.md)

## 后续方向

建议按以下优先级推进：

1. 扩展智能查询模板和同义词识别。
2. 报名名单 CSV 导出。
3. 操作审计日志。
4. 登录失败限流和生产日志规范。
5. 反馈与统计看板增强。
