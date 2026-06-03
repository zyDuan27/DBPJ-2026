# SQL 初始化说明

## 新数据库初始化

新开发环境只需要执行：

```bash
docker compose down -v
docker compose up -d mysql
```

Docker 会在 MySQL 数据卷为空时自动执行：

```text
sql/schema.sql
```

`schema.sql` 是当前唯一的新库主初始化脚本，已经整合：

- 核心建表 SQL。
- 主键、外键、唯一约束、检查约束。
- 级联删除和置空规则。
- 业务触发器。
- 查询路径索引。
- 演示账号和初始测试数据。

## 脚本用途

| 脚本 | 用途 |
| --- | --- |
| `schema.sql` | 新数据库完整初始化入口 |
| `phase2_feedback.sql` | 旧库补齐活动反馈表 |
| `phase2_credit.sql` | 旧库补齐信用流水表 |
| `phase3_cascade_rules.sql` | 旧库补齐或更新级联规则 |
| `phase3_query_indexes.sql` | 旧库补齐或更新查询索引 |
| `migrate_password_hash.sql` | 旧演示库明文密码迁移为 PBKDF2 哈希 |
| `fix_seed_utf8.sql` | 旧容器中文 seed 乱码修复 |
| `performance_checks.sql` | 常用查询执行计划检查 |

## 注意事项

MySQL 官方镜像只会在数据目录为空时执行 `/docker-entrypoint-initdb.d` 下的初始化脚本。

因此，普通的：

```bash
docker compose restart mysql
```

不会重新执行 `schema.sql`。如果需要重新初始化表结构和演示数据，必须先删除 volume：

```bash
docker compose down -v
docker compose up -d mysql
```

旧数据库如果不方便重建，可以按需执行 phase/migrate/fix 脚本；新数据库不要重复执行这些增量脚本。
