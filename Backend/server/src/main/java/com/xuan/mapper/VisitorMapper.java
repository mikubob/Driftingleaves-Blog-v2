package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.VisitorQueryDTO;
import com.xuan.entity.BlogVisitor;
import com.xuan.vo.VisitorVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 访客数据访问接口
 */
@Mapper
public interface VisitorMapper extends BaseMapper<BlogVisitor> {

    /**
     * 幂等创建访客记录，并发首次访问由唯一键串行化且不覆盖已有封禁状态。
     *
     * @param fingerprint 访客统计标识
     * @param now 当前 UTC 时间
     * @return 受影响行数
     */
    int ensure(@Param("fingerprint") String fingerprint, @Param("now") LocalDateTime now);

    /**
     * 按统计标识加行锁查询访客记录
     *
     * @param fingerprint 访客统计标识
     * @return 锁定的访客记录
     */
    BlogVisitor lockFingerprint(@Param("fingerprint") String fingerprint);

    /**
     * 按访客 ID 加行锁查询访客记录
     *
     * @param id 访客 ID
     * @return 锁定的访客记录，不存在时返回 null
     */
    BlogVisitor lockById(@Param("id") long id);

    /**
     * 更新访客最近访问时间、IP、地域与 User-Agent，不修改封禁字段。
     *
     * @param row 访客实体
     * @return 受影响行数
     */
    int visit(@Param("row") BlogVisitor row);

    /**
     * 更新访客封禁状态、封禁 IP 与到期时间，不回写访问记录字段。
     *
     * @param row 访客实体
     * @return 受影响行数
     */
    int restriction(@Param("row") BlogVisitor row);

    /**
     * 检查指定 IP 是否存在生效中的手动封禁记录
     *
     * @param ip 客户端 IP
     * @param now 当前 UTC 时间
     * @return 是否已被封禁
     */
    boolean hasActiveIpBlock(@Param("ip") String ip, @Param("now") LocalDateTime now);

    /**
     * 分页查询访客列表及浏览次数统计
     *
     * @param page 分页参数
     * @param query 查询条件
     * @return 访客分页结果
     */
    IPage<VisitorVO> visitorPage(IPage<VisitorVO> page, @Param("query") VisitorQueryDTO query);

    /**
     * 根据访客 ID 查询访客详情及浏览次数统计
     *
     * @param id 访客 ID
     * @return 访客详情，不存在时返回 null
     */
    VisitorVO visitorDetail(@Param("id") long id);
}
