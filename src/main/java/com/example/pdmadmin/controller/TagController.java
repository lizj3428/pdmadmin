package com.example.pdmadmin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.pdmadmin.entity.NewsTag;
import com.example.pdmadmin.entity.Tag;
import com.example.pdmadmin.mapper.NewsTagMapper;
import com.example.pdmadmin.mapper.TagMapper;
import com.example.pdmadmin.response.R;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 资讯标签管理
 * 【作用】标签是比分类更细的内容标记（考研 / 实习 / 讲座…），
 *        也是后续「按标签定向推送」的依据（论文表4-6、表4-7）。
 */
@io.swagger.v3.oas.annotations.tags.Tag(name = "资讯标签管理", description = "标签的增删改查，引用次数实时统计")
@RestController
@SaCheckRole("admin")
public class TagController {

    @Resource
    private TagMapper tagMapper;
    @Resource
    private NewsTagMapper newsTagMapper;

    @Operation(summary = "标签分页列表", description = "支持按名称模糊搜索；useCount 为实时统计值，不读数据库中那一列")
    @PostMapping("/tag/list")
    @SaCheckLogin
    public R<PageInfo<Tag>> list(@RequestBody Tag tag,
                                 @RequestParam Integer pageNum,
                                 @RequestParam Integer pageSize) {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(tag.getName() != null && !tag.getName().trim().isEmpty(),
                Tag::getName, tag.getName());
        wrapper.orderByDesc(Tag::getId);

        PageHelper.startPage(pageNum, pageSize);
        List<Tag> list = tagMapper.selectList(wrapper);
        PageInfo<Tag> pageInfo = new PageInfo<>(list);

        // 实时统计引用次数：一条 group by 查出当前页所有标签的引用数，再填进 useCount
        // 为什么不维护数据库里那列：挂/摘标签时 +1/-1，并发下会算错，而且永远对不上账
        if (!list.isEmpty()) {
            List<Long> tagIds = list.stream().map(Tag::getId).toList();
            List<Map<String, Object>> rows = newsTagMapper.selectMaps(new QueryWrapper<NewsTag>()
                    // 别名显式写成 camelCase：MyBatis 开了下划线转驼峰，直接 select tag_id
                    // 返回的 key 可能被转成 tagId，两边对不上就查不到值
                    .select("tag_id as tagId", "count(*) as cnt")
                    .in("tag_id", tagIds)
                    .groupBy("tag_id"));
            for (Tag item : list) {
                item.setUseCount(0);
                for (Map<String, Object> row : rows) {
                    if (item.getId().equals(toLong(row.get("tagId")))) {
                        item.setUseCount(toLong(row.get("cnt")).intValue());
                        break;
                    }
                }
            }
        }
        return R.data(pageInfo);
    }

    /** JDBC 在不同驱动/别名写法下 count 可能返回 Long 或 BigInteger，统一转成 long */
    private Long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    @Operation(summary = "新增标签", description = "名称不能为空，且不能与已有标签重名")
    @PostMapping("/tag/add")
    @SaCheckLogin
    public R add(@RequestBody Tag tag) {
        String name = tag.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("标签名称不能为空！");
        }
        Long count = tagMapper.selectCount(
                new LambdaQueryWrapper<Tag>().eq(Tag::getName, name.trim()));
        if (count > 0) {
            throw new RuntimeException("标签名称已存在！");
        }
        tag.setName(name.trim());
        tag.setUseCount(0);
        tag.setCreateTime(LocalDateTime.now());
        tagMapper.insert(tag);
        return R.success();
    }

    @Operation(summary = "修改标签", description = "改名时排除自己，避免把自己判定成重名")
    @PostMapping("/tag/update")
    @SaCheckLogin
    public R update(@RequestBody Tag tag) {
        if (tag.getId() == null) {
            throw new RuntimeException("标签ID不能为空！");
        }
        String name = tag.getName();
        if (name != null && !name.trim().isEmpty()) {
            Long count = tagMapper.selectCount(new LambdaQueryWrapper<Tag>()
                    .eq(Tag::getName, name.trim())
                    .ne(Tag::getId, tag.getId()));
            if (count > 0) {
                throw new RuntimeException("标签名称已存在！");
            }
            tag.setName(name.trim());
        }
        // useCount 是实时算出来的，不落库，这里强制置 null 防止被前端传的值写进去
        tag.setUseCount(null);
        tagMapper.updateById(tag);
        return R.success();
    }

    @Operation(summary = "删除标签", description = "支持批量，同时删除这些标签与资讯的关联记录")
    @PostMapping("/tag/del")
    @SaCheckLogin
    public R del(@RequestParam List<Long> ids) {
        // 先删关联：不删的话新闻详情里会查出指向不存在标签的 id
        newsTagMapper.delete(new LambdaQueryWrapper<NewsTag>().in(NewsTag::getTagId, ids));
        tagMapper.deleteByIds(ids);
        return R.success();
    }
}
