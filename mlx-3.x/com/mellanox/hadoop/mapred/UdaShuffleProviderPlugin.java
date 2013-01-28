/*
** Copyright (C) 2012 Auburn University
** Copyright (C) 2012 Mellanox Technologies
** 
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at:
**  
** http://www.apache.org/licenses/LICENSE-2.0
** 
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
** either express or implied. See the License for the specific language 
** governing permissions and  limitations under the License.
**
**
*/
package com.mellanox.hadoop.mapred;

import org.apache.hadoop.mapred.*;

import java.io.IOException;
import java.net.URI;
import java.net.URL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.conf.Configuration;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.LocalDirAllocator;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.TaskAttemptID;

public class UdaShuffleProviderPlugin implements ShuffleProviderPlugin{

	protected TaskTracker taskTracker; //handle to parent object

	private static final Log LOG = LogFactory.getLog(UdaShuffleProviderPlugin.class.getName());

	// This is the channel used to transfer the data between RDMA C++ and Hadoop
	private UdaPluginTT rdmaChannel;

	
	/**
	 * Do the real constructor work here.  It's in a separate method
	 * so we can call it again and "recycle" the object after calling
	 * close().
	 * 
	 * invoked at the end of TaskTracker.initialize
	 */
	public void initialize(TaskTracker taskTracker) {
		this.taskTracker = taskTracker;
		rdmaChannel = new UdaPluginTT(taskTracker, taskTracker.getJobConf(), this);
	}

	/**
	 * close and cleanup any resource, including threads and disk space.  
	 * A new object within the same process space might be restarted, 
	 * so everything must be clean.
	 * 
	 * invoked at the end of TaskTracker.close
	 */
	public void destroy() {
		rdmaChannel.close();
	}
				  
	
	  /**
	   * a task is done.
	   * invoked at the end of TaskTracker.done, we'll check whether task.isMapTask()
	   */ 
	public void taskDone(Task task, LocalDirAllocator localDirAllocator) {

		if (task.isMapTask()) {
			try {
				String jobId = task.getJobID().toString();
				String taskId = task.getTaskID().toString();
				String userName = task.getUser();
				String intermediateOutputDir = taskTracker.getIntermediateOutputDir(userName, jobId, taskId);
				
				Configuration conf = task.getConf();
				
				Path fout = localDirAllocator.getLocalPathToRead(intermediateOutputDir + "/file.out", conf);
				Path fidx = localDirAllocator.getLocalPathToRead(intermediateOutputDir + "/file.out.index", conf);

				rdmaChannel.notifyMapDone(userName, jobId, taskId, fout, fidx);		
			} catch (org.apache.hadoop.util.DiskChecker.DiskErrorException dee) {
				LOG.debug("TT: DiskErrorException when handling map done - probably OK (map was not created)");
			} catch (IOException ioe) {
				LOG.error("TT: Error when notify map done\n" + StringUtils.stringifyException(ioe));
			}
		}
	}

	  /**
	   * The task tracker is done with this job, so we need to clean up.
	   * 
	   * invoked at the end of TaskTracker.jobDone
	   * @param action The action with the job
	   */
	public void jobDone(JobID jobID) {
		rdmaChannel.jobOver(jobID.toString());
	}
	
	JobConf getJobConfFromSuperClass(JobID jobid) {
		return taskTracker.getJobConf(jobid) ;
	}
	
	static String getIntermediateOutputDirFromSuperClass(String user, String jobid, String taskid) {
 		return TaskTracker.getIntermediateOutputDir(user, jobid, taskid) ;
 	}


	
}
