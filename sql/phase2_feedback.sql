SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS ActivityFeedback (
    feedback_id INT AUTO_INCREMENT PRIMARY KEY,
    registration_id INT NOT NULL COMMENT '对应报名记录，一条报名最多一条评价',
    activity_id INT NOT NULL COMMENT '评价活动',
    student_id INT NOT NULL COMMENT '评价学生',
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
    CONSTRAINT chk_feedback_rating CHECK (rating BETWEEN 1 AND 5),
    KEY idx_feedback_activity_rating_updated (activity_id, rating, updated_at),
    KEY idx_feedback_activity_updated (activity_id, updated_at),
    KEY idx_feedback_student_activity (student_id, activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
