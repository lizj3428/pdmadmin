DELETE m FROM message m
JOIN message k
  ON k.student_id = m.student_id
 AND k.news_id = m.news_id
 AND k.id > m.id;

SET @message_index_ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'message'
          AND index_name = 'uk_msg_student_news'
    ),
    'DO 0',
    'ALTER TABLE message ADD UNIQUE KEY uk_msg_student_news (student_id, news_id)'
);
PREPARE message_index_statement FROM @message_index_ddl;
EXECUTE message_index_statement;
DEALLOCATE PREPARE message_index_statement;
