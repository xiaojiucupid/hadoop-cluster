package com.cev.movie.types.common;

import java.util.Collections;
import java.util.List;

/**
 * 通用分页结果。
 *
 * <p>用于统一接口返回的分页结构，避免每个业务模块重复定义分页字段。</p>
 *
 * @param records 当前页数据
 * @param total 总记录数
 * @param pageNo 当前页码
 * @param pageSize 每页数量
 * @param <T> 记录类型
 */
public record PageResult<T>(List<T> records, long total, int pageNo, int pageSize) {

    public PageResult {
        records = records == null ? Collections.emptyList() : records;
    }

    /**
     * 构造空分页结果。
     */
    public static <T> PageResult<T> empty(PageQuery query) {
        return new PageResult<>(Collections.emptyList(), 0, query.getPageNo(), query.getPageSize());
    }
}
