package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.OperationLogQueryDTO;
import com.xuan.entity.SysOperationLog;
import com.xuan.vo.OperationLogVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 操作日志数据接口，只允许服务端新增，后台可以查询和清理，不能编辑。
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<SysOperationLog> {
    /** 列表明确排除操作数据列，筛选与总数一致。 */
    IPage<OperationLogVO> logPage(IPage<OperationLogVO> page, @Param("query") OperationLogQueryDTO query,
            @Param("user") Long user, @Param("target") Long target,
            @Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);
    /** 清理事务先锁操作者，再锁日志并重新检查本次ALL权限。 */
    SysOperationLog lockById(@Param("id") long id);
    /**
     * 日志不提供编辑；成功日志随业务事务写，失败日志等回滚后在新事务写。
     * 操作者已被物理删除时保存 NULL，与历史日志的外键清理规则一致。
     *
     * @param log 服务端记录的操作信息、执行时的业务参数和固定错误码
     * @return 插入行数
     */
    @Insert("""
            INSERT INTO sys_operation_log
                (user_id, operation_type, operation_target, target_id, permission_code,
                 operation_data, result, error_message, ip_address, operation_time)
            VALUES ((SELECT user_id FROM sys_user WHERE user_id = #{userId}),
                    #{operationType}, #{operationTarget}, #{targetId}, #{permissionCode},
                    #{operationData}, #{result}, #{errorMessage}, #{ipAddress}, #{operationTime})
            """)
    int insertLog(SysOperationLog log);
}
