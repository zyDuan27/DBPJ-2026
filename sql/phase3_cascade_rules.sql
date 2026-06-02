-- ======================================================
-- 第三阶段增量：字典层级删除级联规则
-- 适用于已经初始化过的数据库；新库直接使用 schema.sql 即可。
-- ======================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

SET @schema_name := DATABASE();

SELECT COUNT(*) INTO @has_fk
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = @schema_name
  AND TABLE_NAME = 'Activity'
  AND CONSTRAINT_NAME = 'fk_act_venue';
SET @sql := IF(@has_fk > 0, 'ALTER TABLE Activity DROP FOREIGN KEY fk_act_venue', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @has_fk
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = @schema_name
  AND TABLE_NAME = 'Activity'
  AND CONSTRAINT_NAME = 'fk_act_category';
SET @sql := IF(@has_fk > 0, 'ALTER TABLE Activity DROP FOREIGN KEY fk_act_category', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @has_fk
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = @schema_name
  AND TABLE_NAME = 'Venue'
  AND CONSTRAINT_NAME = 'fk_venue_campus';
SET @sql := IF(@has_fk > 0, 'ALTER TABLE Venue DROP FOREIGN KEY fk_venue_campus', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @has_fk
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = @schema_name
  AND TABLE_NAME = 'Registration'
  AND CONSTRAINT_NAME = 'fk_reg_activity';
SET @sql := IF(@has_fk > 0, 'ALTER TABLE Registration DROP FOREIGN KEY fk_reg_activity', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @has_fk
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = @schema_name
  AND TABLE_NAME = 'ActivityFeedback'
  AND CONSTRAINT_NAME = 'fk_feedback_registration';
SET @sql := IF(@has_fk > 0, 'ALTER TABLE ActivityFeedback DROP FOREIGN KEY fk_feedback_registration', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @has_fk
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = @schema_name
  AND TABLE_NAME = 'ActivityFeedback'
  AND CONSTRAINT_NAME = 'fk_feedback_activity';
SET @sql := IF(@has_fk > 0, 'ALTER TABLE ActivityFeedback DROP FOREIGN KEY fk_feedback_activity', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @has_fk
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = @schema_name
  AND TABLE_NAME = 'CreditRecord'
  AND CONSTRAINT_NAME = 'fk_credit_activity';
SET @sql := IF(@has_fk > 0, 'ALTER TABLE CreditRecord DROP FOREIGN KEY fk_credit_activity', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @has_fk
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = @schema_name
  AND TABLE_NAME = 'CreditRecord'
  AND CONSTRAINT_NAME = 'fk_credit_registration';
SET @sql := IF(@has_fk > 0, 'ALTER TABLE CreditRecord DROP FOREIGN KEY fk_credit_registration', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE Venue
    ADD CONSTRAINT fk_venue_campus
    FOREIGN KEY (campus_id)
    REFERENCES Campus(campus_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE;

ALTER TABLE Activity
    ADD CONSTRAINT fk_act_venue
    FOREIGN KEY (venue_id)
    REFERENCES Venue(venue_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE;

ALTER TABLE Activity
    ADD CONSTRAINT fk_act_category
    FOREIGN KEY (category_id)
    REFERENCES Category(category_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE;

ALTER TABLE Registration
    ADD CONSTRAINT fk_reg_activity
    FOREIGN KEY (activity_id)
    REFERENCES Activity(activity_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE;

ALTER TABLE ActivityFeedback
    ADD CONSTRAINT fk_feedback_registration
    FOREIGN KEY (registration_id)
    REFERENCES Registration(registration_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE;

ALTER TABLE ActivityFeedback
    ADD CONSTRAINT fk_feedback_activity
    FOREIGN KEY (activity_id)
    REFERENCES Activity(activity_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE;

ALTER TABLE CreditRecord
    ADD CONSTRAINT fk_credit_activity
    FOREIGN KEY (activity_id)
    REFERENCES Activity(activity_id)
    ON UPDATE CASCADE
    ON DELETE SET NULL;

ALTER TABLE CreditRecord
    ADD CONSTRAINT fk_credit_registration
    FOREIGN KEY (registration_id)
    REFERENCES Registration(registration_id)
    ON UPDATE CASCADE
    ON DELETE SET NULL;
