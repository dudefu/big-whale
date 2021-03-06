package com.meiyou.bigwhale.task.common;

import com.meiyou.bigwhale.common.Constant;
import com.meiyou.bigwhale.entity.CmdRecord;
import com.meiyou.bigwhale.entity.Scheduling;
import com.meiyou.bigwhale.task.AbstractCmdRecordTask;
import com.meiyou.bigwhale.util.SchedulerUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * @author Suxy
 * @date 2019/9/6
 * @description file description
 */
@DisallowConcurrentExecution
public class CmdRecordTimeoutJob extends AbstractCmdRecordTask implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(CmdRecordTimeoutJob.class);

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        //未开始执行和正在执行的
        Collection<CmdRecord> cmdRecords = cmdRecordService.findByQuery("status=" + Constant.EXEC_STATUS_UNSTART + "," + Constant.EXEC_STATUS_DOING);
        if (CollectionUtils.isEmpty(cmdRecords)) {
            return;
        }
        for (CmdRecord cmdRecord : cmdRecords) {
            Date now = new Date();
            if (cmdRecord.getTimeout() == null) {
                cmdRecord.setTimeout(5);
            }
            int timeout = cmdRecord.getTimeout();
            long ago = DateUtils.addMinutes(now, -timeout).getTime();
            //执行超时
            if (cmdRecord.getCreateTime().getTime() <= ago) {
                cmdRecord.setStatus(Constant.EXEC_STATUS_TIMEOUT);
                //处理调度
                try {
                    JobKey jobKey = new JobKey(cmdRecord.getId(), Constant.JobGroup.CMD);
                    if (cmdRecord.getStatus() == Constant.EXEC_STATUS_UNSTART) {
                        SchedulerUtils.getScheduler().deleteJob(jobKey);
                    } else {
                        SchedulerUtils.getScheduler().interrupt(jobKey);
                    }
                } catch (SchedulerException e) {
                    LOGGER.error(e.getMessage(), e);
                }
                Scheduling scheduling = StringUtils.isNotBlank(cmdRecord.getSchedulingId()) ? schedulingService.findById(cmdRecord.getSchedulingId()) : null;
                notice(cmdRecord, scheduling, null, Constant.ERROR_TYPE_TIMEOUT);
                cmdRecordService.save(cmdRecord);
                //重试
                retryCurrentNode(cmdRecord, scheduling);
            }
        }
    }

}
