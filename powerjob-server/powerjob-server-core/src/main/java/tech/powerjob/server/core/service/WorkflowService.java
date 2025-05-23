package tech.powerjob.server.core.service;

import tech.powerjob.server.persistence.remote.model.WorkflowInfoDO;

/**
 * 工作流服务接口
 *
 * @author echo
 * @since 2025/5/23
 */
public interface WorkflowService {

    /**
     * 原子化调度工作流（保证创建工作流实例和刷新下次调度时间的原子性）
     *
     * @param wfInfo  工作流信息
     */
    void atomicScheduleWorkflow(WorkflowInfoDO wfInfo);
}
