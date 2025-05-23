package tech.powerjob.server.core.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.powerjob.common.enums.SwitchableStatus;
import tech.powerjob.common.enums.TimeExpressionType;
import tech.powerjob.common.model.LifeCycle;
import tech.powerjob.server.common.timewheel.holder.InstanceTimeWheelService;
import tech.powerjob.server.core.scheduler.TimingStrategyService;
import tech.powerjob.server.core.service.WorkflowService;
import tech.powerjob.server.core.workflow.WorkflowInstanceManager;
import tech.powerjob.server.persistence.remote.model.WorkflowInfoDO;
import tech.powerjob.server.persistence.remote.repository.WorkflowInfoRepository;

import java.util.Date;

/**
 * 工作流服务实现类
 *
 * @author echo
 * @since 2025/5/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowInstanceManager workflowInstanceManager;

    private final WorkflowInfoRepository workflowInfoRepository;

    private final TimingStrategyService timingStrategyService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void atomicScheduleWorkflow(WorkflowInfoDO wfInfo) {
        log.info("[Workflow-{}] try to schedule workflow: {}", wfInfo.getId(), wfInfo);

        try {
            // 1. 先生成调度记录
            Long wfInstanceId = workflowInstanceManager.create(wfInfo, null, wfInfo.getNextTriggerTime(), null);

            // 2. 计算下次调度时间并更新工作流表
            LifeCycle lifeCycle = LifeCycle.parse(wfInfo.getLifecycle());
            Long nextTriggerTime = timingStrategyService.calculateNextTriggerTime(wfInfo.getNextTriggerTime(), TimeExpressionType.CRON, wfInfo.getTimeExpression(), lifeCycle.getStart(), lifeCycle.getEnd());

            WorkflowInfoDO updateEntity = new WorkflowInfoDO();
            BeanUtils.copyProperties(wfInfo, updateEntity);

            if (nextTriggerTime == null) {
                log.warn("[Workflow-{}] this workflow won't be scheduled anymore, system will set the status to DISABLE!", wfInfo.getId());
                updateEntity.setStatus(SwitchableStatus.DISABLE.getV());
            } else {
                updateEntity.setNextTriggerTime(nextTriggerTime);
            }
            updateEntity.setGmtModified(new Date());
            workflowInfoRepository.save(updateEntity);
            workflowInfoRepository.flush();

            // 3. 推入时间轮，准备调度执行
            long delay = wfInfo.getNextTriggerTime() - System.currentTimeMillis();
            if (delay < 0) {
                log.warn("[Workflow-{}] workflow schedule delay, expect:{}, actual: {}", wfInfo.getId(), wfInfo.getNextTriggerTime(), System.currentTimeMillis());
                delay = 0;
            }
            InstanceTimeWheelService.schedule(wfInstanceId, delay, () ->
                workflowInstanceManager.start(wfInfo, wfInstanceId)
            );

            log.info("[Workflow-{}|{}] schedule workflow successfully, nextTriggerTime: {}", wfInfo.getId(), wfInstanceId, nextTriggerTime);
        } catch (Exception e) {
            log.error("[Workflow-{}] schedule workflow failed.", wfInfo.getId(), e);
            throw e;
        }
    }
}
