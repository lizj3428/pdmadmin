package com.example.pdmadmin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.pdmadmin.entity.Category;
import com.example.pdmadmin.entity.Message;
import com.example.pdmadmin.entity.News;
import com.example.pdmadmin.entity.NewsTag;
import com.example.pdmadmin.entity.Student;
import com.example.pdmadmin.entity.Subscribe;
import com.example.pdmadmin.entity.Tag;
import com.example.pdmadmin.mapper.CategoryMapper;
import com.example.pdmadmin.mapper.MessageMapper;
import com.example.pdmadmin.mapper.NewsMapper;
import com.example.pdmadmin.mapper.NewsTagMapper;
import com.example.pdmadmin.mapper.StudentMapper;
import com.example.pdmadmin.mapper.SubscribeMapper;
import com.example.pdmadmin.mapper.TagMapper;
import com.example.pdmadmin.response.R;
import com.example.pdmadmin.util.LoginUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生端
 * 【作用】普通学生用的那一套接口：看资讯、收消息、订阅标签、看个人信息。
 *        和管理端是两条完全分开的线，走 /stu 前缀。
 *
 * 【权限】类上是 @SaCheckRole("student")，不是 admin。
 *        所以管理员 token 调这里会 403——他不是学生，没有「我的消息」这回事。
 *        被授权进管理端的学生（admin_flag=1）角色是 ["admin","student"]，两边都能用。
 *
 * 【越权防护】所有「我的」数据都从登录态里取 studentId，不接受前端传。
 *            尤其是消息已读，必须校验这条消息是不是发给自己的，
 *            否则把 id 改成别人的就能把别人的消息标成已读。
 */
// 这里必须写全限定名：本类要用 entity 的 Tag 实体，
// 和 Swagger 的 @Tag 注解同名，import 两个会编译失败
@io.swagger.v3.oas.annotations.tags.Tag(name = "学生端", description = "学生查看资讯、消息中心、订阅与个人信息")
@RestController
@SaCheckRole("student")
public class StuController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private NewsMapper newsMapper;
    @Resource
    private NewsTagMapper newsTagMapper;
    @Resource
    private TagMapper tagMapper;
    @Resource
    private CategoryMapper categoryMapper;
    @Resource
    private MessageMapper messageMapper;
    @Resource
    private SubscribeMapper subscribeMapper;
    @Resource
    private StudentMapper studentMapper;

    // ==================== 资讯 ====================

    @Operation(summary = "学生端资讯列表", description = "只返回已审核且已发布的资讯，可按标题、分类、标签筛选")
    @PostMapping("/stu/news/list")
    @SaCheckLogin
    public R<PageInfo<News>> newsList(@RequestBody News query,
                                      @RequestParam Integer pageNum,
                                      @RequestParam Integer pageSize,
                                      @RequestParam(required = false) Long tagId) {
        LambdaQueryWrapper<News> wrapper = new LambdaQueryWrapper<>();
        // 这两个条件是硬编码的：待审核、被驳回、已下线的稿子学生一律看不到，
        // 前端传什么 publishStatus 都改不了
        wrapper.eq(News::getAuditStatus, 1);
        wrapper.eq(News::getPublishStatus, 1);

        wrapper.like(query.getTitle() != null && !query.getTitle().trim().isEmpty(),
                News::getTitle, query.getTitle());
        wrapper.eq(query.getCategoryId() != null, News::getCategoryId, query.getCategoryId());
        wrapper.eq(query.getLevel() != null, News::getLevel, query.getLevel());
        if (tagId != null) {
            // 用子查询而不是先查出 id 列表再 in()：标签下资讯多的时候，in 的参数个数会爆
            wrapper.apply("id IN (SELECT news_id FROM news_tag WHERE tag_id = {0})", tagId);
        }
        // 重要的排前面，同级别按发布时间倒序
        wrapper.orderByDesc(News::getLevel).orderByDesc(News::getPublishTime);

        PageHelper.startPage(pageNum, pageSize);
        List<News> list = newsMapper.selectList(wrapper);
        PageInfo<News> pageInfo = new PageInfo<>(list);
        // 列表不带正文，省流量；正文走详情接口
        list.forEach(item -> item.setContent(null));
        return R.data(pageInfo);
    }

    @Operation(summary = "学生端资讯详情", description = "只能看已发布的资讯，阅读量 +1，返回标签名")
    @PostMapping("/stu/news/detail")
    @SaCheckLogin
    public R<News> newsDetail(@RequestParam Long id) {
        News news = newsMapper.selectById(id);
        if (news == null || news.getAuditStatus() == null || news.getAuditStatus() != 1
                || news.getPublishStatus() == null || news.getPublishStatus() != 1) {
            throw new RuntimeException("资讯不存在或未发布！");
        }
        // SQL 自增，避免并发打开时互相覆盖
        newsMapper.update(null, new LambdaUpdateWrapper<News>()
                .eq(News::getId, id)
                .setSql("view_count = view_count + 1"));
        news.setViewCount(news.getViewCount() == null ? 1 : news.getViewCount() + 1);

        List<Long> tagIds = newsTagMapper.selectList(
                        new LambdaQueryWrapper<NewsTag>().eq(NewsTag::getNewsId, id))
                .stream().map(NewsTag::getTagId).toList();
        news.setTagNames(tagIds.isEmpty() ? List.of()
                : tagMapper.selectByIds(tagIds).stream().map(Tag::getName).toList());
        return R.data(news);
    }

    @Operation(summary = "学生端分类列表", description = "只返回启用状态的分类，用于首页筛选")
    @PostMapping("/stu/category/list")
    @SaCheckLogin
    public R<List<Category>> categories() {
        return R.data(categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, 1)
                .orderByAsc(Category::getSort)));
    }

    @Operation(summary = "学生端标签列表", description = "用于首页筛选和个人中心的订阅选择")
    @PostMapping("/stu/tag/list")
    @SaCheckLogin
    public R<List<Tag>> tags() {
        return R.data(tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .orderByDesc(Tag::getUseCount)));
    }

    // ==================== 消息中心 ====================

    @Operation(summary = "我的消息列表", description = "只返回发给自己的消息，可按已读/未读筛选")
    @PostMapping("/stu/message/list")
    @SaCheckLogin
    public R<PageInfo<Message>> messageList(@RequestBody Message query,
                                            @RequestParam Integer pageNum,
                                            @RequestParam Integer pageSize) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        // student_id 从登录态取，前端传别人的 id 也没用
        wrapper.eq(Message::getStudentId, LoginUtil.currentId());
        wrapper.eq(query.getReadFlag() != null, Message::getReadFlag, query.getReadFlag());
        wrapper.orderByDesc(Message::getCreateTime);

        PageHelper.startPage(pageNum, pageSize);
        return R.data(new PageInfo<>(messageMapper.selectList(wrapper)));
    }

    @Operation(summary = "标记单条消息已读", description = "只能标记发给自己的消息")
    @PostMapping("/stu/message/read")
    @SaCheckLogin
    public R read(@RequestParam Long id) {
        // update 的 where 里同时带 id 和 student_id：
        // 传别人的消息 id 会命中 0 行，不会报错也不会改到别人的数据
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getId, id)
                .eq(Message::getStudentId, LoginUtil.currentId())
                .set(Message::getReadFlag, 1));
        return R.success();
    }

    @Operation(summary = "全部标记已读")
    @PostMapping("/stu/message/readAll")
    @SaCheckLogin
    public R readAll() {
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getStudentId, LoginUtil.currentId())
                .eq(Message::getReadFlag, 0)
                .set(Message::getReadFlag, 1));
        return R.success();
    }

    @Operation(summary = "未读消息数", description = "用于顶部小红点")
    @PostMapping("/stu/message/unread")
    @SaCheckLogin
    public R<Long> unread() {
        return R.data(messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getStudentId, LoginUtil.currentId())
                .eq(Message::getReadFlag, 0)));
    }

    // ==================== 个人信息与订阅 ====================

    @Operation(summary = "我的信息", description = "返回当前登录学生的资料，不含密码密文")
    @PostMapping("/stu/profile")
    @SaCheckLogin
    public R<Student> profile() {
        Student student = studentMapper.selectById(LoginUtil.currentId());
        if (student == null) {
            throw new RuntimeException("学生不存在！");
        }
        student.setPassword(null);
        return R.data(student);
    }

    @Operation(summary = "我的订阅", description = "返回订阅的分类和标签，带名称方便前端直接展示")
    @PostMapping("/stu/subscribe/list")
    @SaCheckLogin
    public R<List<Map<String, Object>>> subscribeList() {
        List<Subscribe> list = subscribeMapper.selectList(new LambdaQueryWrapper<Subscribe>()
                .eq(Subscribe::getStudentId, LoginUtil.currentId()));
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
        return R.data(result);
    }

    @Operation(summary = "保存我的订阅", description = "先清空该类型的旧订阅再写入，传空数组表示全部取消")
    @PostMapping("/stu/subscribe/save")
    @SaCheckLogin
    public R saveSubscribe(@RequestParam(defaultValue = "2") Integer targetType,
                           @RequestParam(required = false) List<Long> targetIds) {
        if (targetType != 1 && targetType != 2) {
            throw new RuntimeException("订阅类型只能是 1（分类）或 2（标签）！");
        }
        Long studentId = LoginUtil.currentId();
        // 先删后插：前端每次提交的是「我最终要订阅哪些」，整体覆盖比逐个比对增删可靠
        subscribeMapper.delete(new LambdaQueryWrapper<Subscribe>()
                .eq(Subscribe::getStudentId, studentId)
                .eq(Subscribe::getTargetType, targetType));
        if (targetIds == null || targetIds.isEmpty()) {
            return R.success();
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
        return R.success();
    }
}
