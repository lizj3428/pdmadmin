package com.example.pdmadmin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.pdmadmin.entity.Category;
import com.example.pdmadmin.mapper.CategoryMapper;
import com.example.pdmadmin.response.R;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 资讯分类管理
 * 【作用】分类是资讯的归属（教务 / 活动 / 招聘…），发资讯必须先有分类。
 *        直接用 CategoryMapper，不再套一层 Service：BaseMapper 已经提供增删改查，
 *        这里没有任何事务或多表逻辑，加 Service 只会多两个空壳文件。
 */
@Tag(name = "资讯分类管理", description = "分类的增删改查")
@RestController
public class CategoryController {

    @Resource
    private CategoryMapper categoryMapper;

    @Operation(summary = "分类分页列表", description = "支持按名称模糊搜索、按状态筛选，按 sort 升序")
    @PostMapping("/category/list")
    @SaCheckLogin
    public R<PageInfo<Category>> list(@RequestBody Category category,
                                      @RequestParam Integer pageNum,
                                      @RequestParam Integer pageSize) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        // 名称模糊搜：没传就不加这个条件
        wrapper.like(category.getName() != null && !category.getName().trim().isEmpty(),
                Category::getName, category.getName());
        // 状态筛选：1启用 0停用
        wrapper.eq(category.getStatus() != null, Category::getStatus, category.getStatus());
        // sort 越小越靠前，前端显示顺序由它决定
        wrapper.orderByAsc(Category::getSort);

        PageHelper.startPage(pageNum, pageSize);
        List<Category> list = categoryMapper.selectList(wrapper);
        return R.data(new PageInfo<>(list));
    }

    @Operation(summary = "新增分类", description = "名称不能为空，且不能与已有分类重名")
    @PostMapping("/category/add")
    @SaCheckLogin
    public R add(@RequestBody Category category) {
        // 名称查重：category 表 name 上有唯一索引，
        // 这里先查一次是为了返回友好提示，而不是让数据库抛 Duplicate entry
        String name = category.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("分类名称不能为空！");
        }
        Long count = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>().eq(Category::getName, name));
        if (count > 0) {
            throw new RuntimeException("分类名称已存在！");
        }
        categoryMapper.insert(category);
        return R.success();
    }

    @Operation(summary = "修改分类", description = "改名时排除自己，避免把自己判定成重名")
    @PostMapping("/category/update")
    @SaCheckLogin
    public R update(@RequestBody Category category) {
        String name = category.getName();
        if (name != null && !name.trim().isEmpty()) {
            // ne(id)：查重时把自己排除掉，否则改别的字段也会撞自己的名字
            Long count = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                    .eq(Category::getName, name)
                    .ne(Category::getId, category.getId()));
            if (count > 0) {
                throw new RuntimeException("分类名称已存在！");
            }
        }
        categoryMapper.updateById(category);
        return R.success();
    }

    @Operation(summary = "删除分类", description = "支持批量，按 ids 删除")
    @PostMapping("/category/del")
    @SaCheckLogin
    public R del(@RequestParam List<Long> ids) {
        // 注意：这里不校验分类下是否还挂着资讯。
        // 资讯模块还没做完，现在加这个校验等于写两遍；等资讯模块完成后补上。
        categoryMapper.deleteByIds(ids);
        return R.success();
    }

    @Operation(summary = "启用分类列表", description = "不分页，返回所有启用的分类，供前端下拉框使用")
    @PostMapping("/category/all")
    @SaCheckLogin
    public R<List<Category>> all() {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Category::getStatus, 1);
        wrapper.orderByAsc(Category::getSort);
        return R.data(categoryMapper.selectList(wrapper));
    }
}
