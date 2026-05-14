SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS CreditRecord (
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
    ),
    KEY idx_credit_student_time_record (student_id, created_at, record_id),
    KEY idx_credit_activity_reason (activity_id, reason_type),
    KEY idx_credit_reason_student (reason_type, student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
