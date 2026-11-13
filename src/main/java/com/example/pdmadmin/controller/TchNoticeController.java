package com.example.pdmadmin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.pdmadmin.entity.Message;
import com.example.pdmadmin.mapper.MessageMapper;
import com.example.pdmadmin.response.R;
import com.example.pdmadmin.util.LoginUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 教师消息通知
 *
 * 【数据从哪来】审核接口（NewsController.audit）在教师投稿审核完成后，
 *        往 message 表插一条 receiver_type=2、msg_type=3 的通知，
 *        接收人 ID 存在 student_id 列里（列名沿用历史命名）。
 *
 * 【越权防护】三个接口的 where 条件都同时带 receiver_type=2 和当前教师 id，
 *        前端传别人的消息 id 会命中 0 行，改不到别人的数据。
 */
@io.swagger.v3.oas.annotations.tags.Tag(name = "教师消息通知", description = "教师查看审核结果通知")
@RestController
@SaCheckRole("teacher")
public class TchNoticeController {

    @Resource
    private MessageMapper messageMapper;

    @Operation(summary = "我的通知列表", description = "只返回发给当前教师的审核结果通知，按时间倒序")
    @PostMapping("/tch/notice/list")
    @SaCheckLogin
    public R<PageInfo<Message>> list(@RequestBody(required = false) Message query,
                                     @RequestParam(defaultValue = "1") Integer pageNum,
                                     @RequestParam(defaultValue = "10") Integer pageSize) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getReceiverType, 2);
        wrapper.eq(Message::getStudentId, LoginUtil.currentId());
        if (query != null) {
            wrapper.eq(query.getReadFlag() != null, Message::getReadFlag, query.getReadFlag());
        }
        wrapper.orderByDesc(Message::getCreateTime);

        PageHelper.startPage(pageNum, pageSize);
        List<Message> list = messageMapper.selectList(wrapper);
        return R.data(new PageInfo<>(list));
    }

    @Operation(summary = "标记通知已读", description = "只能标记发给自己的通知")
    @PostMapping("/tch/notice/read")
    @SaCheckLogin
    public R read(@RequestParam Long id) {
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getId, id)
                .eq(Message::getReceiverType, 2)
                .eq(Message::getStudentId, LoginUtil.currentId())
                .set(Message::getReadFlag, 1));
        return R.success();
    }

    @Operation(summary = "未读通知数", description = "用于教师端菜单上的小红点")
    @PostMapping("/tch/notice/unread")
    @SaCheckLogin
    public R<Long> unread() {
        return R.data(messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getReceiverType, 2)
                .eq(Message::getStudentId, LoginUtil.currentId())
                .eq(Message::getReadFlag, 0)));
    }
}
