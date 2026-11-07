CREATE TABLE IF NOT EXISTS news_read_record (
    student_id  BIGINT   NOT NULL                           COMMENT '学生ID',
    news_id     BIGINT   NOT NULL                           COMMENT '资讯ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次阅读时间',
    PRIMARY KEY (student_id, news_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学生资讯阅读记录';
