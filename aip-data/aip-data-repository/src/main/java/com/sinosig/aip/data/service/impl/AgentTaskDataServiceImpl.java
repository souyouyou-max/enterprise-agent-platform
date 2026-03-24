package com.sinosig.aip.data.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.sinosig.aip.common.core.response.PageResult;
import com.sinosig.aip.data.entity.AgentSubTask;
import com.sinosig.aip.data.entity.AgentTask;
import com.sinosig.aip.data.mapper.AgentSubTaskMapper;
import com.sinosig.aip.data.mapper.AgentTaskMapper;
import com.sinosig.aip.data.service.AgentTaskDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 任务数据服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskDataServiceImpl implements AgentTaskDataService {

    private final AgentTaskMapper agentTaskMapper;
    private final AgentSubTaskMapper agentSubTaskMapper;

    @Override
    @Transactional
    public AgentTask createTask(String taskName, String goal) {
        AgentTask task = new AgentTask();
        task.setTaskName(taskName);
        task.setGoal(goal);
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        agentTaskMapper.insert(task);
        log.info("[DataService] 创建任务, id={}", task.getId());
        return task;
    }

    @Override
    public AgentTask getById(Long id) {
        return agentTaskMapper.selectById(id);
    }

    @Override
    public PageResult<AgentTask> getPage(long page, long size) {
        long offset = (page - 1) * size;
        List<AgentTask> records = agentTaskMapper.findPage(offset, size);
        long total = agentTaskMapper.countAll();
        return PageResult.of(records, total, page, size);
    }

    @Override
    @Transactional
    public void updateStatus(Long taskId, String status) {
        UpdateWrapper<AgentTask> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", taskId)
               .set("status", status)
               .set("updated_at", LocalDateTime.now());
        agentTaskMapper.update(null, wrapper);
    }

    @Override
    @Transactional
    public boolean updateStatusIfCurrent(Long taskId, String expectedCurrentStatus, String targetStatus) {
        UpdateWrapper<AgentTask> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", taskId)
                .eq("status", expectedCurrentStatus)
                .set("status", targetStatus)
                .set("updated_at", LocalDateTime.now());
        return agentTaskMapper.update(null, wrapper) > 0;
    }

    @Override
    @Transactional
    public void updateTaskResult(Long taskId, String plannerResult, String executorResult,
                                  Integer reviewerScore, String finalReport, String status) {
        UpdateWrapper<AgentTask> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", taskId)
               .set(plannerResult != null, "planner_result", plannerResult)
               .set(executorResult != null, "executor_result", executorResult)
               .set(reviewerScore != null, "reviewer_score", reviewerScore)
               .set(finalReport != null, "final_report", finalReport)
               .set("status", status)
               .set("updated_at", LocalDateTime.now());
        agentTaskMapper.update(null, wrapper);
    }

    @Override
    @Transactional
    public boolean updateTaskResultIfCurrent(Long taskId, String expectedCurrentStatus,
                                             String plannerResult, String executorResult,
                                             Integer reviewerScore, String finalReport, String targetStatus) {
        UpdateWrapper<AgentTask> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", taskId)
                .eq("status", expectedCurrentStatus)
                .set(plannerResult != null, "planner_result", plannerResult)
                .set(executorResult != null, "executor_result", executorResult)
                .set(reviewerScore != null, "reviewer_score", reviewerScore)
                .set(finalReport != null, "final_report", finalReport)
                .set("status", targetStatus)
                .set("updated_at", LocalDateTime.now());
        return agentTaskMapper.update(null, wrapper) > 0;
    }

    @Override
    @Transactional
    public void saveSubTasks(Long taskId, List<AgentSubTask> subTasks) {
        agentSubTaskMapper.deleteByTaskId(taskId);
        subTasks.forEach(subTask -> {
            subTask.setTaskId(taskId);
            subTask.setCreatedAt(LocalDateTime.now());
            agentSubTaskMapper.insert(subTask);
        });
    }

    @Override
    public List<AgentSubTask> getSubTasks(Long taskId) {
        return agentSubTaskMapper.findByTaskId(taskId);
    }
}
