package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.OperationLogQueryDTO;
import com.xuan.entity.SysOperationLog;
import com.xuan.result.PageResult;
import com.xuan.vo.OperationLogDetailVO;
import com.xuan.vo.OperationLogVO;

import java.util.List;

/**
 * 后台日志只读或显式清理，不能创建或编辑。
 */
public interface IOperationLogService extends IService<SysOperationLog> {

    /** ALL范围内按明确字段分页。 */
    PageResult<OperationLogVO> listLogs(OperationLogQueryDTO query);

    /** ALL 管理员读取操作基本信息与已记录的完整业务参数。 */
    OperationLogDetailVO detail(long id);

    /** 清理和清理成功审计一起提交，不存在404。 */
    void delete(long id);
    /** 单个或多个目标共用入口，全部数据库变更共同提交。 */
    void deleteBatch(List<String> ids);
}
