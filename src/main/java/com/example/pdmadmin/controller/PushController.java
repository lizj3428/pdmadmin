package com.example.pdmadmin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.pdmadmin.entity.PushRecord;
import com.example.pdmadmin.entity.PushTask;
import com.example.pdmadmin.mapper.PushRecordMapper;
import com.example.pdmadmin.mapper.PushTaskMapper;
import com.example.pdmadmin.response.R;
import com.example.pdmadmin.service.PushService;
import com.example.pdmadmin.util.LoginUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 推送管理
 * 【作用】管理员挑一条资讯、选定目标人群，让平台把消息推到学生手上。
 *        立即推送在接口里当场执行；定时推送落库后由 PushScheduler 扫表触发。
 *
 * 【两类推送的差别只在一个字段】
 * push_type=1 立即：insert 完直接调 PushService.execute
 * push_type=2 定时：insert 完就返回，等计划时间到了由调度器执行
 */
@Tag(name = "推送管理", description = "推送任务的创建、执行、查询与推送记录")
@RestController
@SaCheckRole("admin")
public class PushController {

    @Resource
    private PushTaskMapper pushTaskMapper;
    @Resource
    private PushRecordMapper pushRecordMapper;
    @Resource
    private PushService pushService;

    @Operation(summary = "新增推送任务", description = "立即推送当场执行；定时推送等 planTime 到点由调度器执行")
    @PostMapping("/push/add")
    @SaCheckLogin
    public R add(@RequestBody PushTask task) {
        // 1. 基础参数校验
        if (task.getNewsId() == null) {
            throw new RuntimeException("请选择要推送的资讯！");
        }
        if (task.getTaskName() == null || task.getTaskName().trim().isEmpty()) {
            throw new RuntimeException("任务名称不能为空！");
        }
        if (task.getTargetType() == null) {
            throw new RuntimeException("请选择目标人群类型！");
        }
        // 按年级/专业/标签定向必须给出取值，否则等于没选目标
        if (task.getTargetType() != 1
                && (task.getTargetValue() == null || task.getTargetValue().trim().isEmpty())) {
            throw new RuntimeException("请填写目标人群取值！");
        }
        if (task.getChannel() == null) {
            task.setChannel(1); // 默认站内信
        }
        if (task.getPushType() == null) {
            task.setPushType(1); // 默认立即
        }
        if (task.getPushType() == 2 && task.getPlanTime() == null) {
            throw new RuntimeException("定时推送必须填写计划推送时间！");
        }

        // 2. 状态和统计字段一律由后端决定，不信前端传来的值
        task.setStatus(0);        // 待执行
        task.setTargetCount(0);
        task.setCreateBy(LoginUtil.currentId());
        task.setCreateTime(LocalDateTime.now());
        pushTaskMapper.insert(task);

        // 3. 立即推送：当场执行；定时推送：直接返回
        if (task.getPushType() == 1) {
            pushService.execute(task);
        }
        return R.success();
    }

    @Operation(summary = "推送任务分页列表", description = "支持按任务名称模糊搜索、按状态/推送方式筛选")
    @PostMapping("/push/list")
    @SaCheckLogin
    public R<PageInfo<PushTask>> list(@RequestBody PushTask task,
                                      @RequestParam Integer pageNum,
                                      @RequestParam Integer pageSize) {
        LambdaQueryWrapper<PushTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(task.getTaskName() != null && !task.getTaskName().trim().isEmpty(),
                PushTask::getTaskName, task.getTaskName());
        wrapper.eq(task.getStatus() != null, PushTask::getStatus, task.getStatus());
        wrapper.eq(task.getPushType() != null, PushTask::getPushType, task.getPushType());
        wrapper.orderByDesc(PushTask::getId);

        PageHelper.startPage(pageNum, pageSize);
        List<PushTask> list = pushTaskMapper.selectList(wrapper);
        return R.data(new PageInfo<>(list));
    }

    @Operation(summary = "手动执行推送任务", description = "用于重跑待执行或失败的任务；执行中的任务不允许重复执行")
    @PostMapping("/push/run")
    @SaCheckLogin
    public R run(@RequestParam Long taskId) {
        PushTask task = pushTaskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("推送任务不存在！");
        }
        if (task.getStatus() != null && task.getStatus() == 1) {
            throw new RuntimeException("任务正在执行中，请勿重复触发！");
        }
        pushService.execute(task);
        return R.success();
    }

    @Operation(summary = "删除推送任务", description = "支持批量，连带删除这些任务的推送记录")
    @PostMapping("/push/del")
    @SaCheckLogin
    public R del(@RequestParam List<Long> ids) {
        // 先删记录再删任务：反过来的话记录会指向一个已经不存在的任务
        pushRecordMapper.delete(new LambdaQueryWrapper<PushRecord>().in(PushRecord::getTaskId, ids));
        pushTaskMapper.deleteByIds(ids);
        return R.success();
    }

    @Operation(summary = "推送记录分页列表", description = "查看某个任务对每个学生的发送结果、耗时与失败原因")
    @PostMapping("/push/records")
    @SaCheckLogin
    public R<PageInfo<PushRecord>> records(@RequestParam Long taskId,
                                           @RequestParam Integer pageNum,
                                           @RequestParam Integer pageSize) {
        LambdaQueryWrapper<PushRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PushRecord::getTaskId, taskId);
        wrapper.orderByDesc(PushRecord::getId);

        PageHelper.startPage(pageNum, pageSize);
        List<PushRecord> list = pushRecordMapper.selectList(wrapper);
        return R.data(new PageInfo<>(list));
    }

    @Operation(summary = "预估目标人数", description = "只算人数不发送，供前端在提交前显示「本次将推给 N 人」")
    @PostMapping("/push/preview")
    @SaCheckLogin
    public R<Integer> preview(@RequestParam Integer targetType,
                              @RequestParam(required = false) String targetValue) {
        return R.data(pushService.resolveTargets(targetType, targetValue).size());
    }
}
