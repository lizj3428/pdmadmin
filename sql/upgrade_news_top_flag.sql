SET @news_top_flag_ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'news'
          AND column_name = 'top_flag'
    ),
    'DO 0',
    'ALTER TABLE news ADD COLUMN top_flag TINYINT NOT NULL DEFAULT 0 COMMENT ''置顶状态 0普通 1置顶'''
);
PREPARE news_top_flag_statement FROM @news_top_flag_ddl;
EXECUTE news_top_flag_statement;
DEALLOCATE PREPARE news_top_flag_statement;
