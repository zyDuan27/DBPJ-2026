-- ======================================================
-- 项目名称：校园活动报名系统
-- 版本：数据库优化版
-- 数据库：MySQL 8.4.8 LTS
-- ======================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS ActivityFeedback;
DROP TABLE IF EXISTS CreditRecord;
DROP TABLE IF EXISTS Registration;
DROP TABLE IF EXISTS Activity;
DROP TABLE IF EXISTS Category;
DROP TABLE IF EXISTS Venue;
DROP TABLE IF EXISTS Campus;
DROP TABLE IF EXISTS User;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE User (
    user_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '用户唯一 ID',
    role VARCHAR(20) NOT NULL COMMENT 'STUDENT / ORGANIZER / ADMIN',
    username VARCHAR(50) NOT NULL COMMENT '姓名或组织名称',
    student_no VARCHAR(20) UNIQUE COMMENT '学号，学生账号使用',
    password VARCHAR(255) NOT NULL COMMENT 'PBKDF2 哈希后的登录密码',
    phone VARCHAR(20) UNIQUE NOT NULL COMMENT '联系电话',
    CONSTRAINT chk_user_role CHECK (role IN ('STUDENT', 'ORGANIZER', 'ADMIN')),
    CONSTRAINT chk_user_student_no CHECK (
        (role = 'STUDENT' AND student_no IS NOT NULL)
        OR (role IN ('ORGANIZER', 'ADMIN') AND student_no IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户，按 role 区分学生、组织者和管理员';

CREATE TABLE Campus (
    campus_id INT AUTO_INCREMENT PRIMARY KEY,
    campus_name VARCHAR(100) NOT NULL UNIQUE COMMENT '校区名',
    location VARCHAR(255) COMMENT '校区位置'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校区字典';

CREATE TABLE Venue (
    venue_id INT AUTO_INCREMENT PRIMARY KEY,
    venue_name VARCHAR(100) NOT NULL COMMENT '场馆名称',
    room_number VARCHAR(50) NOT NULL COMMENT '房间号',
    capacity INT NOT NULL COMMENT '场地容量',
    campus_id INT NOT NULL COMMENT '所属校区',
    CONSTRAINT fk_venue_campus FOREIGN KEY (campus_id)
        REFERENCES Campus(campus_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT uq_venue_room UNIQUE (campus_id, venue_name, room_number),
    CONSTRAINT chk_venue_capacity CHECK (capacity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动场地字典';

CREATE TABLE Category (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(50) NOT NULL UNIQUE COMMENT '分类名称'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动分类字典';

CREATE TABLE Activity (
    activity_id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL COMMENT '活动标题',
    start_time DATETIME NOT NULL COMMENT '活动开始时间',
    end_time DATETIME NOT NULL COMMENT '活动结束时间',
    enroll_deadline DATETIME NOT NULL COMMENT '报名截止时间',
    capacity_limit INT NOT NULL COMMENT '名额上限',
    current_enrollment INT NOT NULL DEFAULT 0 COMMENT '当前占用名额人数：ENROLLED + CHECKED_IN',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' COMMENT '活动状态',
    reject_reason VARCHAR(500) COMMENT '审核驳回原因',
    poster_url VARCHAR(500) COMMENT '活动海报地址',
    description TEXT COMMENT '活动详情',
    venue_id INT NOT NULL,
    category_id INT NOT NULL,
    organizer_id INT NOT NULL,
    admin_id INT,
    CONSTRAINT fk_act_venue FOREIGN KEY (venue_id)
        REFERENCES Venue(venue_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_act_category FOREIGN KEY (category_id)
        REFERENCES Category(category_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_act_organizer FOREIGN KEY (organizer_id)
        REFERENCES User(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_act_admin FOREIGN KEY (admin_id)
        REFERENCES User(user_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT chk_act_capacity CHECK (capacity_limit > 0),
    CONSTRAINT chk_act_current CHECK (current_enrollment >= 0 AND current_enrollment <= capacity_limit),
    CONSTRAINT chk_act_time CHECK (end_time > start_time),
    CONSTRAINT chk_act_deadline CHECK (enroll_deadline <= start_time),
    CONSTRAINT chk_act_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'REJECTED', 'PUBLISHED', 'ONGOING', 'FINISHED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动主表';

CREATE TABLE Registration (
    registration_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL COMMENT '报名学生',
    activity_id INT NOT NULL COMMENT '报名活动',
    registration_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
    status VARCHAR(30) NOT NULL COMMENT '报名状态',
    queue_no INT COMMENT '候补序号，仅 WAITLISTED 使用',
    check_in_time DATETIME COMMENT '签到时间，仅 CHECKED_IN 使用',
    CONSTRAINT fk_reg_student FOREIGN KEY (student_id)
        REFERENCES User(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_reg_activity FOREIGN KEY (activity_id)
        REFERENCES Activity(activity_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT uq_reg_student_activity UNIQUE (student_id, activity_id),
    CONSTRAINT chk_reg_status CHECK (status IN ('ENROLLED', 'WAITLISTED', 'CANCELLED', 'CHECKED_IN', 'ABSENT')),
    CONSTRAINT chk_reg_queue CHECK (
        (status = 'WAITLISTED' AND queue_no IS NOT NULL AND queue_no > 0)
        OR (status <> 'WAITLISTED' AND queue_no IS NULL)
    ),
    CONSTRAINT chk_reg_check_in CHECK (
        (status = 'CHECKED_IN' AND check_in_time IS NOT NULL)
        OR (status <> 'CHECKED_IN' AND check_in_time IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报名记录';

CREATE TABLE ActivityFeedback (
    feedback_id INT AUTO_INCREMENT PRIMARY KEY,
    registration_id INT NOT NULL COMMENT '对应报名记录，一条报名最多一条评价',
    activity_id INT NOT NULL COMMENT '评价活动，冗余自 Registration 便于统计',
    student_id INT NOT NULL COMMENT '评价学生，冗余自 Registration 便于检索',
    rating TINYINT NOT NULL COMMENT '1-5 星评分',
    content VARCHAR(1000) COMMENT '文字反馈',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次评价时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT fk_feedback_registration FOREIGN KEY (registration_id)
        REFERENCES Registration(registration_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_feedback_activity FOREIGN KEY (activity_id)
        REFERENCES Activity(activity_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_feedback_student FOREIGN KEY (student_id)
        REFERENCES User(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT uq_feedback_registration UNIQUE (registration_id),
    CONSTRAINT chk_feedback_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动评价';

CREATE TABLE CreditRecord (
    record_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL COMMENT '学生',
    activity_id INT COMMENT '关联活动',
    registration_id INT COMMENT '关联报名记录',
    change_value INT NOT NULL COMMENT '信用分变化值',
    reason_type VARCHAR(30) NOT NULL COMMENT 'CHECK_IN / ABSENT / MANUAL_ADJUST',
    reason VARCHAR(500) COMMENT '信用变化说明',
    operator_id INT COMMENT '操作人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    CONSTRAINT fk_credit_student FOREIGN KEY (student_id)
        REFERENCES User(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_credit_activity FOREIGN KEY (activity_id)
        REFERENCES Activity(activity_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT fk_credit_registration FOREIGN KEY (registration_id)
        REFERENCES Registration(registration_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT fk_credit_operator FOREIGN KEY (operator_id)
        REFERENCES User(user_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT uq_credit_reason_registration UNIQUE (reason_type, registration_id),
    CONSTRAINT chk_credit_reason CHECK (reason_type IN ('CHECK_IN', 'ABSENT', 'MANUAL_ADJUST')),
    CONSTRAINT chk_credit_value CHECK (
        (reason_type = 'CHECK_IN' AND change_value > 0)
        OR (reason_type = 'ABSENT' AND change_value < 0)
        OR reason_type = 'MANUAL_ADJUST'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信用分流水';

-- 与登录、活动列表、报名名单、候补递补、反馈看板和信用统计匹配的索引。
CREATE INDEX idx_user_role ON User(role);
CREATE INDEX idx_user_username ON User(username);
CREATE INDEX idx_activity_status_time ON Activity(status, start_time);
CREATE INDEX idx_activity_category_status_time ON Activity(category_id, status, start_time);
CREATE INDEX idx_activity_organizer_status_time ON Activity(organizer_id, status, start_time);
CREATE INDEX idx_activity_venue_time ON Activity(venue_id, start_time, end_time);
CREATE INDEX idx_registration_activity_status_queue_time ON Registration(activity_id, status, queue_no, registration_time);
CREATE INDEX idx_registration_student_status_activity ON Registration(student_id, status, activity_id);
CREATE INDEX idx_feedback_activity_rating_updated ON ActivityFeedback(activity_id, rating, updated_at);
CREATE INDEX idx_feedback_activity_updated ON ActivityFeedback(activity_id, updated_at);
CREATE INDEX idx_feedback_student_activity ON ActivityFeedback(student_id, activity_id);
CREATE INDEX idx_credit_student_time_record ON CreditRecord(student_id, created_at, record_id);
CREATE INDEX idx_credit_activity_reason ON CreditRecord(activity_id, reason_type);
CREATE INDEX idx_credit_reason_student ON CreditRecord(reason_type, student_id);

DELIMITER //

CREATE TRIGGER trg_activity_bi_validate
BEFORE INSERT ON Activity
FOR EACH ROW
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM User
        WHERE user_id = NEW.organizer_id AND role = 'ORGANIZER'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '活动组织者必须是 ORGANIZER 用户';
    END IF;

    IF NEW.admin_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM User
        WHERE user_id = NEW.admin_id AND role = 'ADMIN'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '活动审核人必须是 ADMIN 用户';
    END IF;

    IF NEW.capacity_limit > (
        SELECT capacity FROM Venue WHERE venue_id = NEW.venue_id
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '活动名额不能超过场地容量';
    END IF;

    IF NEW.status IN ('PENDING_REVIEW', 'PUBLISHED', 'ONGOING')
       AND EXISTS (
           SELECT 1
           FROM Activity a
           WHERE a.venue_id = NEW.venue_id
             AND a.status IN ('PENDING_REVIEW', 'PUBLISHED', 'ONGOING')
             AND NEW.start_time < a.end_time
             AND NEW.end_time > a.start_time
       ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '同一场地存在时间冲突的活动';
    END IF;
END//

CREATE TRIGGER trg_activity_bu_validate
BEFORE UPDATE ON Activity
FOR EACH ROW
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM User
        WHERE user_id = NEW.organizer_id AND role = 'ORGANIZER'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '活动组织者必须是 ORGANIZER 用户';
    END IF;

    IF NEW.admin_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM User
        WHERE user_id = NEW.admin_id AND role = 'ADMIN'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '活动审核人必须是 ADMIN 用户';
    END IF;

    IF NEW.capacity_limit > (
        SELECT capacity FROM Venue WHERE venue_id = NEW.venue_id
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '活动名额不能超过场地容量';
    END IF;

    IF (OLD.venue_id <> NEW.venue_id
        OR OLD.status <> NEW.status
        OR OLD.start_time <> NEW.start_time
        OR OLD.end_time <> NEW.end_time)
       AND NEW.status IN ('PENDING_REVIEW', 'PUBLISHED', 'ONGOING')
       AND EXISTS (
           SELECT 1
           FROM Activity a
           WHERE a.activity_id <> NEW.activity_id
             AND a.venue_id = NEW.venue_id
             AND a.status IN ('PENDING_REVIEW', 'PUBLISHED', 'ONGOING')
             AND NEW.start_time < a.end_time
             AND NEW.end_time > a.start_time
       ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '同一场地存在时间冲突的活动';
    END IF;
END//

CREATE TRIGGER trg_registration_bi_validate
BEFORE INSERT ON Registration
FOR EACH ROW
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM User
        WHERE user_id = NEW.student_id AND role = 'STUDENT'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '报名用户必须是 STUDENT';
    END IF;

    IF NEW.status IN ('ENROLLED', 'CHECKED_IN')
       AND (SELECT current_enrollment FROM Activity WHERE activity_id = NEW.activity_id)
           >= (SELECT capacity_limit FROM Activity WHERE activity_id = NEW.activity_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '活动名额已满';
    END IF;
END//

CREATE TRIGGER trg_registration_bu_validate
BEFORE UPDATE ON Registration
FOR EACH ROW
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM User
        WHERE user_id = NEW.student_id AND role = 'STUDENT'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '报名用户必须是 STUDENT';
    END IF;

    IF OLD.status NOT IN ('ENROLLED', 'CHECKED_IN')
       AND NEW.status IN ('ENROLLED', 'CHECKED_IN')
       AND (SELECT current_enrollment FROM Activity WHERE activity_id = NEW.activity_id)
           >= (SELECT capacity_limit FROM Activity WHERE activity_id = NEW.activity_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '活动名额已满';
    END IF;
END//

CREATE TRIGGER trg_feedback_bi_validate
BEFORE INSERT ON ActivityFeedback
FOR EACH ROW
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM Registration r
        WHERE r.registration_id = NEW.registration_id
          AND r.student_id = NEW.student_id
          AND r.activity_id = NEW.activity_id
          AND r.status = 'CHECKED_IN'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '只有已签到报名记录可以评价，且评价学生和活动必须与报名一致';
    END IF;
END//

CREATE TRIGGER trg_feedback_bu_validate
BEFORE UPDATE ON ActivityFeedback
FOR EACH ROW
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM Registration r
        WHERE r.registration_id = NEW.registration_id
          AND r.student_id = NEW.student_id
          AND r.activity_id = NEW.activity_id
          AND r.status = 'CHECKED_IN'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '只有已签到报名记录可以评价，且评价学生和活动必须与报名一致';
    END IF;
END//

CREATE TRIGGER trg_credit_bi_validate
BEFORE INSERT ON CreditRecord
FOR EACH ROW
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM User
        WHERE user_id = NEW.student_id AND role = 'STUDENT'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '信用流水学生必须是 STUDENT';
    END IF;

    IF NEW.operator_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM User
        WHERE user_id = NEW.operator_id AND role IN ('ORGANIZER', 'ADMIN')
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '信用流水操作人必须是 ORGANIZER 或 ADMIN';
    END IF;

    IF NEW.registration_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM Registration r
        WHERE r.registration_id = NEW.registration_id
          AND r.student_id = NEW.student_id
          AND (NEW.activity_id IS NULL OR r.activity_id = NEW.activity_id)
          AND (
              (NEW.reason_type = 'CHECK_IN' AND r.status = 'CHECKED_IN')
              OR (NEW.reason_type = 'ABSENT' AND r.status = 'ABSENT')
              OR NEW.reason_type = 'MANUAL_ADJUST'
          )
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '信用流水与报名记录不一致';
    END IF;
END//

DELIMITER ;

-- 演示初始化数据，默认登录密码均为 123456，数据库只保存 PBKDF2 哈希。
INSERT INTO User(role, username, student_no, password, phone) VALUES
('STUDENT', '学生张三', '20230001', 'pbkdf2$120000$ZGJwai0yMDI2LXN0dWRlbnQtc2VlZA==$8Qp3YWW+CCkG53R6RiFjJHtofp7RjSeqMnd2lIiWiNY=', '13800000001'),
('STUDENT', '学生李四', '20230002', 'pbkdf2$120000$ZGJwai0yMDI2LXN0dWRlbnQtc2VlZA==$8Qp3YWW+CCkG53R6RiFjJHtofp7RjSeqMnd2lIiWiNY=', '13800000004'),
('ORGANIZER', '计算机协会', NULL, 'pbkdf2$120000$ZGJwai0yMDI2LW9yZ2FuaXplci1zZWVk$Viio6kNxospwiFm3piu3fJy2m2fe0A1eDo/IsGFZfI8=', '13800000002'),
('ADMIN', '系统管理员', NULL, 'pbkdf2$120000$ZGJwai0yMDI2LWFkbWluLXNlZWQ=$6aTjSynvefikLgDvcDa2aX2kpo0dnT+guS8//Q6FKFQ=', '13800000003');

INSERT INTO Campus(campus_name, location) VALUES
('邯郸校区', '上海市杨浦区邯郸路'),
('江湾校区', '上海市杨浦区淞沪路');

INSERT INTO Venue(venue_name, room_number, capacity, campus_id) VALUES
('光华楼', 'H3106', 80, 1),
('逸夫科技楼', 'A201', 50, 1),
('综合体育馆', '主馆', 200, 2);

INSERT INTO Category(category_name) VALUES
('学术讲座'),
('比赛竞赛'),
('文娱活动'),
('志愿服务');

INSERT INTO Activity(
    title, start_time, end_time, enroll_deadline, capacity_limit, current_enrollment,
    status, poster_url, description, venue_id, category_id, organizer_id, admin_id
) VALUES
(
    '数据库系统项目分享会',
    '2026-05-20 14:00:00',
    '2026-05-20 16:00:00',
    '2026-05-19 22:00:00',
    80,
    2,
    'PUBLISHED',
    '',
    '面向数据库课程项目的小型分享会，介绍活动报名系统的设计与实现。',
    1,
    1,
    3,
    4
),
(
    '校园志愿服务培训',
    '2026-05-22 09:00:00',
    '2026-05-22 11:00:00',
    '2026-05-21 18:00:00',
    50,
    0,
    'PUBLISHED',
    '',
    '面向新志愿者的流程培训和经验分享。',
    2,
    4,
    3,
    4
);

INSERT INTO Registration(student_id, activity_id, status, queue_no, check_in_time) VALUES
(1, 1, 'CHECKED_IN', NULL, '2026-05-20 13:55:00'),
(2, 1, 'ENROLLED', NULL, NULL),
(1, 2, 'WAITLISTED', 1, NULL);

INSERT INTO ActivityFeedback(registration_id, activity_id, student_id, rating, content) VALUES
(1, 1, 1, 5, '内容清晰，案例贴近课程项目。');

INSERT INTO CreditRecord(student_id, activity_id, registration_id, change_value, reason_type, reason, operator_id) VALUES
(1, 1, 1, 1, 'CHECK_IN', '按时完成活动签到', 3);
