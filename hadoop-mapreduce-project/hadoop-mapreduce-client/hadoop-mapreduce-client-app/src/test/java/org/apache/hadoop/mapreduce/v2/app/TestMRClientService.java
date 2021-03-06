/**
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.hadoop.mapreduce.v2.app;

import java.util.Iterator;
import java.util.List;

import junit.framework.Assert;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.v2.api.MRClientProtocol;
import org.apache.hadoop.mapreduce.v2.api.protocolrecords.GetCountersRequest;
import org.apache.hadoop.mapreduce.v2.api.protocolrecords.GetDiagnosticsRequest;
import org.apache.hadoop.mapreduce.v2.api.protocolrecords.GetJobReportRequest;
import org.apache.hadoop.mapreduce.v2.api.protocolrecords.GetTaskAttemptCompletionEventsRequest;
import org.apache.hadoop.mapreduce.v2.api.protocolrecords.GetTaskAttemptReportRequest;
import org.apache.hadoop.mapreduce.v2.api.protocolrecords.GetTaskReportRequest;
import org.apache.hadoop.mapreduce.v2.api.protocolrecords.GetTaskReportsRequest;
import org.apache.hadoop.mapreduce.v2.api.records.AMInfo;
import org.apache.hadoop.mapreduce.v2.api.records.JobReport;
import org.apache.hadoop.mapreduce.v2.api.records.JobState;
import org.apache.hadoop.mapreduce.v2.api.records.Phase;
import org.apache.hadoop.mapreduce.v2.api.records.TaskAttemptReport;
import org.apache.hadoop.mapreduce.v2.api.records.TaskAttemptState;
import org.apache.hadoop.mapreduce.v2.api.records.TaskReport;
import org.apache.hadoop.mapreduce.v2.api.records.TaskState;
import org.apache.hadoop.mapreduce.v2.api.records.TaskType;
import org.apache.hadoop.mapreduce.v2.app.client.ClientService;
import org.apache.hadoop.mapreduce.v2.app.client.MRClientService;
import org.apache.hadoop.mapreduce.v2.app.job.Job;
import org.apache.hadoop.mapreduce.v2.app.job.Task;
import org.apache.hadoop.mapreduce.v2.app.job.TaskAttempt;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptDiagnosticsUpdateEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptEventType;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptStatusUpdateEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptStatusUpdateEvent.TaskAttemptStatus;
import org.apache.hadoop.yarn.factories.RecordFactory;
import org.apache.hadoop.yarn.factory.providers.RecordFactoryProvider;
import org.apache.hadoop.yarn.ipc.YarnRPC;
import org.junit.Test;

public class TestMRClientService {

  private static RecordFactory recordFactory = RecordFactoryProvider.getRecordFactory(null);
  
  @Test
  public void test() throws Exception {
    MRAppWithClientService app = new MRAppWithClientService(1, 0, false);
    Configuration conf = new Configuration();
    Job job = app.submit(conf);
    app.waitForState(job, JobState.RUNNING);
    Assert.assertEquals("Num tasks not correct", 1, job.getTasks().size());
    Iterator<Task> it = job.getTasks().values().iterator();
    Task task = it.next();
    app.waitForState(task, TaskState.RUNNING);
    TaskAttempt attempt = task.getAttempts().values().iterator().next();
    app.waitForState(attempt, TaskAttemptState.RUNNING);

    // send the diagnostic
    String diagnostic1 = "Diagnostic1";
    String diagnostic2 = "Diagnostic2";
    app.getContext().getEventHandler().handle(
        new TaskAttemptDiagnosticsUpdateEvent(attempt.getID(), diagnostic1));

    // send the status update
    TaskAttemptStatus taskAttemptStatus = new TaskAttemptStatus();
    taskAttemptStatus.id = attempt.getID();
    taskAttemptStatus.progress = 0.5f;
    taskAttemptStatus.stateString = "RUNNING";
    taskAttemptStatus.taskState = TaskAttemptState.RUNNING;
    taskAttemptStatus.phase = Phase.MAP;
    taskAttemptStatus.outputSize = 3;
    // send the status update
    app.getContext().getEventHandler().handle(
        new TaskAttemptStatusUpdateEvent(attempt.getID(), taskAttemptStatus));

    
    //verify that all object are fully populated by invoking RPCs.
    YarnRPC rpc = YarnRPC.create(conf);
    MRClientProtocol proxy =
      (MRClientProtocol) rpc.getProxy(MRClientProtocol.class,
          app.clientService.getBindAddress(), conf);
    GetCountersRequest gcRequest =
        recordFactory.newRecordInstance(GetCountersRequest.class);    
    gcRequest.setJobId(job.getID());
    Assert.assertNotNull("Counters is null",
        proxy.getCounters(gcRequest).getCounters());

    GetJobReportRequest gjrRequest =
        recordFactory.newRecordInstance(GetJobReportRequest.class);
    gjrRequest.setJobId(job.getID());
    JobReport jr = proxy.getJobReport(gjrRequest).getJobReport();
    verifyJobReport(jr);
    

    GetTaskAttemptCompletionEventsRequest gtaceRequest =
        recordFactory.newRecordInstance(GetTaskAttemptCompletionEventsRequest.class);
    gtaceRequest.setJobId(job.getID());
    gtaceRequest.setFromEventId(0);
    gtaceRequest.setMaxEvents(10);
    Assert.assertNotNull("TaskCompletionEvents is null", 
        proxy.getTaskAttemptCompletionEvents(gtaceRequest).getCompletionEventList());

    GetDiagnosticsRequest gdRequest =
        recordFactory.newRecordInstance(GetDiagnosticsRequest.class);
    gdRequest.setTaskAttemptId(attempt.getID());
    Assert.assertNotNull("Diagnostics is null", 
        proxy.getDiagnostics(gdRequest).getDiagnosticsList());

    GetTaskAttemptReportRequest gtarRequest =
        recordFactory.newRecordInstance(GetTaskAttemptReportRequest.class);
    gtarRequest.setTaskAttemptId(attempt.getID());
    TaskAttemptReport tar =
        proxy.getTaskAttemptReport(gtarRequest).getTaskAttemptReport();
    verifyTaskAttemptReport(tar);
    

    GetTaskReportRequest gtrRequest =
        recordFactory.newRecordInstance(GetTaskReportRequest.class);
    gtrRequest.setTaskId(task.getID());
    Assert.assertNotNull("TaskReport is null", 
        proxy.getTaskReport(gtrRequest).getTaskReport());

    GetTaskReportsRequest gtreportsRequest =
        recordFactory.newRecordInstance(GetTaskReportsRequest.class);
    gtreportsRequest.setJobId(job.getID());
    gtreportsRequest.setTaskType(TaskType.MAP);
    Assert.assertNotNull("TaskReports for map is null", 
        proxy.getTaskReports(gtreportsRequest).getTaskReportList());

    gtreportsRequest =
        recordFactory.newRecordInstance(GetTaskReportsRequest.class);
    gtreportsRequest.setJobId(job.getID());
    gtreportsRequest.setTaskType(TaskType.REDUCE);
    Assert.assertNotNull("TaskReports for reduce is null", 
        proxy.getTaskReports(gtreportsRequest).getTaskReportList());

    List<String> diag = proxy.getDiagnostics(gdRequest).getDiagnosticsList();
    Assert.assertEquals("Num diagnostics not correct", 1 , diag.size());
    Assert.assertEquals("Diag 1 not correct",
        diagnostic1, diag.get(0).toString());

    TaskReport taskReport = proxy.getTaskReport(gtrRequest).getTaskReport();
    Assert.assertEquals("Num diagnostics not correct", 1,
        taskReport.getDiagnosticsCount());

    //send the done signal to the task
    app.getContext().getEventHandler().handle(
        new TaskAttemptEvent(
            task.getAttempts().values().iterator().next().getID(),
            TaskAttemptEventType.TA_DONE));

    app.waitForState(job, JobState.SUCCEEDED);
  }

  private void verifyJobReport(JobReport jr) {
    Assert.assertNotNull("JobReport is null", jr);
    List<AMInfo> amInfos = jr.getAMInfos();
    Assert.assertEquals(1, amInfos.size());
    Assert.assertEquals(JobState.RUNNING, jr.getJobState());
    AMInfo amInfo = amInfos.get(0);
    Assert.assertEquals(MRApp.NM_HOST, amInfo.getNodeManagerHost());
    Assert.assertEquals(MRApp.NM_PORT, amInfo.getNodeManagerPort());
    Assert.assertEquals(MRApp.NM_HTTP_PORT, amInfo.getNodeManagerHttpPort());
    Assert.assertEquals(1, amInfo.getAppAttemptId().getAttemptId());
    Assert.assertEquals(1, amInfo.getContainerId().getApplicationAttemptId()
        .getAttemptId());
    Assert.assertTrue(amInfo.getStartTime() > 0);
    Assert.assertEquals(false, jr.isUber());
  }
  
  private void verifyTaskAttemptReport(TaskAttemptReport tar) {
    Assert.assertEquals(TaskAttemptState.RUNNING, tar.getTaskAttemptState());
    Assert.assertNotNull("TaskAttemptReport is null", tar);
    Assert.assertEquals(MRApp.NM_HOST, tar.getNodeManagerHost());
    Assert.assertEquals(MRApp.NM_PORT, tar.getNodeManagerPort());
    Assert.assertEquals(MRApp.NM_HTTP_PORT, tar.getNodeManagerHttpPort());
    Assert.assertEquals(1, tar.getContainerId().getApplicationAttemptId()
        .getAttemptId());
  }
  
  class MRAppWithClientService extends MRApp {
    MRClientService clientService = null;
    MRAppWithClientService(int maps, int reduces, boolean autoComplete) {
      super(maps, reduces, autoComplete, "MRAppWithClientService", true);
    }
    @Override
    protected ClientService createClientService(AppContext context) {
      clientService = new MRClientService(context);
      return clientService;
    }
  }
  
  public static void main(String[] args) throws Exception {
    TestMRClientService t = new TestMRClientService();
    t.test();
  }
}
