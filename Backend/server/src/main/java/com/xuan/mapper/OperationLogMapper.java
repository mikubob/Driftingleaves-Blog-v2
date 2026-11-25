package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.OperationLogQueryDTO;
import com.xuan.entity.SysOperationLog;
import com.xuan.vo.OperationLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 操作日志数据访问接口
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<SysOperationLog> {

    /**
     * 分页查询操作日志列表，列表不返回操作参数正文。
     *
     * @param page 分页参数
     * @param query 查询条件
     * @param user 操作者用户 ID，可为空
     * @param target 操作目标 ID，可为空
     * @param begin 开始时间，可为空
     * @param end 结束时间，可为空
     * @return 操作日志分页结果
     */
    IPage<OperationLogVO> logPage(IPage<OperationLogVO> page,
            @Param("query") OperationLogQueryDTO query,
            @Param("user") Long user,
            @Param("target") Long target,
            @Param("begin") LocalDateTime begin,
            @Param("end") LocalDateTime end);

    /**
     * 加行锁查询操作日志记录，用于清理前校验。
     *
     * @param id 日志 ID
     * @return 锁定的操作日志记录，不存在时返回 null
     */
    SysOperationLog lockById(@Param("id") long id);

    /**
     * 新增一条操作日志记录，操作者已被物理删除时 user_id 自动写入 NULL。
     *
     * @param log 操作日志实体
     * @return 受影响行数
     */
    int insertLog(SysOperationLog log);
}
