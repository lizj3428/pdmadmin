package com.example.pdmadmin.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface NewsReadRecordMapper {

    @Select("SELECT COUNT(*) FROM news_read_record WHERE student_id = #{studentId} AND news_id = #{newsId}")
    int count(@Param("studentId") Long studentId, @Param("newsId") Long newsId);

    @Insert("INSERT INTO news_read_record (student_id, news_id) VALUES (#{studentId}, #{newsId})")
    void insert(@Param("studentId") Long studentId, @Param("newsId") Long newsId);
}
