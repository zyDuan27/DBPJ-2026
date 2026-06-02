-- ======================================================
-- 常用查询 EXPLAIN 验证脚本
-- 用于课程展示和本地检查查询是否匹配主要索引。
-- ======================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 1. 学生活动大厅：按状态过滤并按开始时间倒序分页。
EXPLAIN
SELECT a.activity_id AS id, a.title,
       a.start_time AS startTime, a.end_time AS endTime,
       c.campus_name AS campusName, v.venue_name AS venueName,
       cat.category_name AS categoryName, u.username AS organizerName
FROM Activity a
JOIN Venue v ON a.venue_id = v.venue_id
JOIN Campus c ON v.campus_id = c.campus_id
JOIN Category cat ON a.category_id = cat.category_id
JOIN User u ON a.organizer_id = u.user_id
WHERE a.status IN ('PUBLISHED', 'ONGOING', 'FINISHED')
ORDER BY a.start_time DESC
LIMIT 20;

-- 2. 组织者活动管理：按组织者、状态和时间分页。
EXPLAIN
SELECT a.activity_id AS id, a.title, a.start_time AS startTime, a.status
FROM Activity a
WHERE a.organizer_id = 3
  AND a.status = 'PUBLISHED'
ORDER BY a.start_time DESC
LIMIT 20;

-- 3. 活动报名名单：按活动和状态过滤，按状态、候补序号、报名时间排序。
EXPLAIN
SELECT r.registration_id AS registrationId, u.username AS studentName,
       u.student_no AS studentNo, r.status, r.queue_no AS queueNo,
       r.registration_time AS registrationTime
FROM Registration r FORCE INDEX (idx_registration_activity_status_queue_time)
JOIN User u ON r.student_id = u.user_id
WHERE r.activity_id = 1
  AND r.status IN ('ENROLLED', 'WAITLISTED', 'CHECKED_IN')
ORDER BY r.status, r.queue_no, r.registration_time
LIMIT 20;

-- 4. 学生信用流水：按学生过滤并按时间倒序分页。
EXPLAIN
SELECT c.record_id AS recordId, c.change_value AS changeValue,
       c.reason_type AS reasonType, c.created_at AS createdAt
FROM CreditRecord c
WHERE c.student_id = 1
ORDER BY c.created_at DESC, c.record_id DESC
LIMIT 20;

-- 5. 通知列表：按接收人过滤，未读优先、时间倒序分页。
EXPLAIN
SELECT notification_id AS notificationId, type, title, is_read AS `read`, created_at AS createdAt
FROM Notification
WHERE recipient_id = 1
ORDER BY is_read ASC, created_at DESC, notification_id DESC
LIMIT 20;

-- 6. 场地时间冲突检测：按场地和时间范围判断重叠。
EXPLAIN
SELECT COUNT(*)
FROM Activity a
WHERE a.venue_id = 1
  AND a.status IN ('PENDING_REVIEW', 'PUBLISHED', 'ONGOING')
  AND TIMESTAMP('2026-05-20 13:00:00') < a.end_time
  AND TIMESTAMP('2026-05-20 15:00:00') > a.start_time;
