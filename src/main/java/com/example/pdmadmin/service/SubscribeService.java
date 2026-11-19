package com.example.pdmadmin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.pdmadmin.entity.Category;
import com.example.pdmadmin.entity.Subscribe;
import com.example.pdmadmin.entity.Tag;
import com.example.pdmadmin.mapper.CategoryMapper;
import com.example.pdmadmin.mapper.SubscribeMapper;
import com.example.pdmadmin.mapper.TagMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订阅业务服务
 * 【作用】学生订阅设置：查询已订阅项、保存订阅选择。
 *        控制器只负责收参数、返回结果，业务规则与事务都在这一层。
 *
 * 【保存语义】整体覆盖，不是增量增删。
 *            前端每次提交的是「我最终要订阅哪些」，
 *            所以先按类型清空旧记录，再批量写入新记录。
 *            传空数组表示该类型全部取消，此时只清不写。
 */
@Service
public class SubscribeService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SubscribeMapper subscribeMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;

    public SubscribeService(SubscribeMapper subscribeMapper, TagMapper tagMapper, CategoryMapper categoryMapper) {
        this.subscribeMapper = subscribeMapper;
        this.tagMapper = tagMapper;
        this.categoryMapper = categoryMapper;
    }

    /**
     * 查询某个学生的全部订阅项。
     * 订阅关系表只存 targetType 与 targetId，名称需要回查标签表或分类表补全；
     * 目标被删除时给一个占位名，避免前端出现空白项。
     */
    public List<Map<String, Object>> listByStudent(Long studentId) {
        List<Subscribe> list = subscribeMapper.selectList(new LambdaQueryWrapper<Subscribe>()
                .eq(Subscribe::getStudentId, studentId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Subscribe s : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("targetType", s.getTargetType());
            item.put("targetId", s.getTargetId());
            if (s.getTargetType() != null && s.getTargetType() == 2) {
                Tag tag = tagMapper.selectById(s.getTargetId());
                item.put("name", tag == null ? "已删除的标签" : tag.getName());
            } else {
                Category category = categoryMapper.selectById(s.getTargetId());
                item.put("name", category == null ? "已删除的分类" : category.getName());
            }
            item.put("createTime", s.getCreateTime() == null ? "" : s.getCreateTime().format(DAY));
            result.add(item);
        }
        return result;
    }

    /**
     * 保存某个学生在指定类型下的订阅。
     * 先删后插，两步在同一事务中完成，任何一步失败都回滚，
     * 避免出现「旧的删了、新的没写进去」的中间状态。
     *
     * @param targetType 1 表示分类，2 表示标签
     * @param targetIds  订阅目标编号，空表示该类型全部取消
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveSubscriptions(Long studentId, Integer targetType, List<Long> targetIds) {
        if (targetType == null || (targetType != 1 && targetType != 2)) {
            throw new RuntimeException("订阅类型只能是 1（分类）或 2（标签）！");
        }
        subscribeMapper.delete(new LambdaQueryWrapper<Subscribe>()
                .eq(Subscribe::getStudentId, studentId)
                .eq(Subscribe::getTargetType, targetType));
        if (targetIds == null || targetIds.isEmpty()) {
            return;
        }
        for (Long targetId : targetIds.stream().distinct().toList()) {
            if (targetId == null) {
                continue;
            }
            Subscribe subscribe = new Subscribe();
            subscribe.setStudentId(studentId);
            subscribe.setTargetType(targetType);
            subscribe.setTargetId(targetId);
            subscribe.setCreateTime(LocalDateTime.now());
            subscribeMapper.insert(subscribe);
        }
    }
}
