package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.VisitorQueryDTO;
import com.xuan.entity.BlogVisitor;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 指纹只作统计唯一键，不能拿它授权或封禁本站账号。
 */
public interface VisitorMapper extends BaseMapper<BlogVisitor> {

    /** 并发首次访问靠唯一键串行，不覆盖限制状态。 */
    int ensure(@Param("fingerprint") String fingerprint, @Param("now") LocalDateTime now);

    /** 采集持锁检查限制，再更新最近访问与写明细。 */
    BlogVisitor lockFingerprint(@Param("fingerprint") String fingerprint);

    /** 管理锁后重查本次独立ALL权限和版本。 */
    BlogVisitor lockById(@Param("id") long id);

    /** 最近访问不改变限制字段。 */
    int visit(@Param("row") BlogVisitor row);

    /** 管理只替换限制字段，不回写旧的访问/IP/UA。 */
    int restriction(@Param("row") BlogVisitor row);

    /** 列表按最近访问和ID倒序，查询条件与total一致。 */
    IPage<BlogVisitor> visitorPage(IPage<BlogVisitor> page, @Param("query") VisitorQueryDTO query);
}
