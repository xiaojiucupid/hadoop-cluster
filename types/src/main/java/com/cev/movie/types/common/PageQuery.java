package com.cev.movie.types.common;

/**
 * 通用分页查询参数。
 *
 * <p>types 层用于沉淀跨模块复用的基础类型，分页对象可被 HTTP 接口、
 * Job 查询和后台管理功能共同使用。</p>
 */
public class PageQuery {

    /** 默认页码，从 1 开始。 */
    private static final int DEFAULT_PAGE_NO = 1;

    /** 默认每页数量。 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 最大每页数量，避免一次查询过多数据。 */
    private static final int MAX_PAGE_SIZE = 100;

    private Integer pageNo;
    private Integer pageSize;

    public PageQuery() {
        this.pageNo = DEFAULT_PAGE_NO;
        this.pageSize = DEFAULT_PAGE_SIZE;
    }

    public PageQuery(Integer pageNo, Integer pageSize) {
        this.pageNo = normalizePageNo(pageNo);
        this.pageSize = normalizePageSize(pageSize);
    }

    public Integer getPageNo() {
        return normalizePageNo(pageNo);
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = normalizePageNo(pageNo);
    }

    public Integer getPageSize() {
        return normalizePageSize(pageSize);
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = normalizePageSize(pageSize);
    }

    /**
     * 计算数据库分页偏移量。
     */
    public long offset() {
        return (long) (getPageNo() - 1) * getPageSize();
    }

    private int normalizePageNo(Integer value) {
        return value == null || value < 1 ? DEFAULT_PAGE_NO : value;
    }

    private int normalizePageSize(Integer value) {
        if (value == null || value < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(value, MAX_PAGE_SIZE);
    }
}
