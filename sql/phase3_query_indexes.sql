-- ======================================================
-- 第三阶段增量：查询路径索引优化
-- 适用于已经初始化过的数据库；新库直接使用 schema.sql 即可。
-- ======================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

SET @schema_name := DATABASE();

-- 活动报名名单：按活动、状态、候补队列和报名时间分页。先建新索引，再删旧索引，避免外键依赖缺少辅助索引。
SELECT COUNT(*) INTO @has_new_idx
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 'Registration'
  AND INDEX_NAME = 'idx_registration_activity_status_queue_time';
SET @sql := IF(@has_new_idx = 0,
    'CREATE INDEX idx_registration_activity_status_queue_time ON Registration(activity_id, status, queue_no, registration_time)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @has_old_idx
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 'Registration'
  AND INDEX_NAME = 'idx_registration_activity_status_queue';
SET @sql := IF(@has_old_idx > 0, 'DROP INDEX idx_registration_activity_status_queue ON Registration', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 我的报名记录：按学生、状态过滤后回表关联活动。
SELECT COUNT(*) INTO @has_new_idx
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 'Registration'
  AND INDEX_NAME = 'idx_registration_student_status_activity';
SET @sql := IF(@has_new_idx = 0,
    'CREATE INDEX idx_registration_student_status_activity ON Registration(student_id, status, activity_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @has_old_idx
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 'Registration'
  AND INDEX_NAME = 'idx_registration_student_status';
SET @sql := IF(@has_old_idx > 0, 'DROP INDEX idx_registration_student_status ON Registration', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 组织者活动管理：组织者 + 状态过滤后按开始时间展示。
SELECT COUNT(*) INTO @has_new_idx
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 'Activity'
  AND INDEX_NAME = 'idx_activity_organizer_status_time';
SET @sql := IF(@has_new_idx = 0,
    'CREATE INDEX idx_activity_organizer_status_time ON Activity(organizer_id, status, start_time)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @has_old_idx
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 'Activity'
  AND INDEX_NAME = 'idx_activity_organizer_status';
SET @sql := IF(@has_old_idx > 0, 'DROP INDEX idx_activity_organizer_status ON Activity', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 分类活动查询：分类 + 状态过滤后按开始时间展示。
SELECT COUNT(*) INTO @has_new_idx
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 'Activity'
  AND INDEX_NAME = 'idx_activity_category_status_time';
SET @sql := IF(@has_new_idx = 0,
    'CREATE INDEX idx_activity_category_status_time ON Activity(category_id, status, start_time)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 学生信用流水：学生维度按时间查询。
SELECT COUNT(*) INTO @has_new_idx
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 'CreditRecord'
  AND INDEX_NAME = 'idx_credit_student_time_record';
SET @sql := IF(@has_new_idx = 0,
    'CREATE INDEX idx_credit_student_time_record ON CreditRecord(student_id, created_at, record_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @has_old_idx
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 'CreditRecord'
  AND INDEX_NAME = 'idx_credit_student_time';
SET @sql := IF(@has_old_idx > 0, 'DROP INDEX idx_credit_student_time ON CreditRecord', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 通知列表：收件人 + 已读状态过滤，按时间倒序稳定分页。
SELECT COUNT(*) INTO @has_old_idx
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 'Notification'
  AND INDEX_NAME = 'idx_notification_recipient_read_time';
SET @sql := IF(@has_old_idx > 0, 'DROP INDEX idx_notification_recipient_read_time ON Notification', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @has_new_idx
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 'Notification'
  AND INDEX_NAME = 'idx_notification_recipient_read_order';
SET @sql := IF(@has_new_idx = 0,
    'CREATE INDEX idx_notification_recipient_read_order ON Notification(recipient_id, is_read, created_at DESC, notification_id DESC)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
