package com.example.pdmadmin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.pdmadmin.entity.PushTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface PushTaskMapper extends BaseMapper<PushTask> {

    /**
     * 抢占任务：只有「待执行(0)」「失败(3)」能被领走，已完成(2)和执行中(1)抢不到。
     * 用带条件的 UPDATE 而不是先查再改，同时触发两次时只有一个能抢到。
     *
     * @return 影响行数，1 表示抢到，0 表示没抢到
     */
    @Update("UPDATE push_task SET status = 1 WHERE id = #{id} AND status IN (0, 3)")
    int claim(@Param("id") Long id);
}
