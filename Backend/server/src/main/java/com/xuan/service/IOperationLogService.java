package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.entity.SysOperationLog;
import com.xuan.dto.OperationLogQueryDTO;
import com.xuan.vo.OperationLogVO;
import com.xuan.vo.OperationLogDetailVO;
import com.xuan.result.PageResult;

/** 后台日志只读或显式清理，不能创建或编辑。 */
public interface IOperationLogService extends IService<SysOperationLog> {
    /** ALL范围内按明确字段分页。 */
    PageResult<OperationLogVO> listLogs(OperationLogQueryDTO query);
    /** 读取脱敏基本信息与白名单摘要。 */
    OperationLogDetailVO detail(long id);
    /** 清理和清理成功审计一起提交，不存在404。 */
    void delete(long id);
}
