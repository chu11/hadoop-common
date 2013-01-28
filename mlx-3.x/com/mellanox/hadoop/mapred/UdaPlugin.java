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

import java.io.File;
import java.io.IOException;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.MemoryHandler;
import java.util.Map.Entry;

import org.apache.hadoop.mapred.Reporter;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.hadoop.util.Progress;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalDirAllocator;
import org.apache.hadoop.io.DataInputBuffer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.DiskChecker;
import org.apache.hadoop.util.DiskChecker.DiskErrorException;
import org.apache.hadoop.io.WritableUtils;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.ContainerLocalizer;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.hadoop.yarn.util.Records;
import org.apache.hadoop.conf.Configuration;


abstract class UdaPlugin {
	static protected final Log LOG = LogFactory.getLog(UdaPlugin.class.getName());
	protected List<String> mCmdParams = new ArrayList<String>();
	protected static JobConf mjobConf;
	
	public UdaPlugin(JobConf jobConf) {
		this.mjobConf=jobConf;
	}
	
	//* build arguments to lunchCppSide 
	protected abstract void buildCmdParams();
	
	protected void launchCppSide(boolean isNetMerger, UdaCallable _callable) {
		
		LOG.info("UDA: Launching C++ thru JNI");
		buildCmdParams();

		LOG.info("going to execute C++ thru JNI with argc=: " + mCmdParams.size() + " cmd: " + mCmdParams);    	  
		String[] stringarray = mCmdParams.toArray(new String[0]);

		try {
			UdaBridge.start(isNetMerger, stringarray, LOG, _callable);

		} catch (UnsatisfiedLinkError e) {
			LOG.warn("UDA: Exception when launching child");    	  
			LOG.warn("java.library.path=" + System.getProperty("java.library.path"));
			LOG.warn(StringUtils.stringifyException(e));
			throw (e);
		}
	}

}

class UdaPluginRT<K,V> extends UdaPlugin implements UdaCallable {

	final UdaShuffleConsumerPlugin udaShuffleConsumer;
	final ReduceTask reduceTask;

	private Reporter      mTaskReporter = null;    
	private Progress          mProgress     = null;
	private Vector<String>    mParams       = new Vector<String>();
	private int               mMapsNeed     = 0;      
	private int               mMapsCount    = 0; // we probably can remove this var
	private int               mReqNums      = 0;
	private final int         mReportCount  = 20;
	private J2CQueue<K,V>     j2c_queue     = null;


	//* kv buf status 
	private final int         kv_buf_recv_ready = 1;
	private final int         kv_buf_redc_ready = 2;
	//* kv buf related vars  
	private final int         kv_buf_size = 1 << 20;   /* 1 MB */
	private final int         kv_buf_num = 2;
	private KVBuf[]           kv_bufs = null;

	private void init_kv_bufs() {
		kv_bufs = new KVBuf[kv_buf_num];
		for (int idx = 0; idx < kv_buf_num; ++idx) {
			kv_bufs[idx] = new KVBuf(kv_buf_size);
		} 
	}

	protected void buildCmdParams() {
		mCmdParams.clear();
		
		mCmdParams.add("-w");
		mCmdParams.add(mjobConf.get("mapred.rdma.wqe.per.conn", "256"));
		mCmdParams.add("-r");
		mCmdParams.add(mjobConf.get("mapred.rdma.cma.port", "9011"));      
		mCmdParams.add("-a");
		mCmdParams.add(mjobConf.get("mapred.netmerger.merge.approach", "1"));
		mCmdParams.add("-m");
		mCmdParams.add("1");
		
		mCmdParams.add("-g");
		mCmdParams.add(TaskLog.getMRv2LogDir() + "/" + reduceTask.getTaskID().toString()+"-");
		//log will be located in applicationXXX/containerYYY/attemptZZZ-udaNetMerger.log
		
		mCmdParams.add("-s");
		mCmdParams.add(mjobConf.get("mapred.rdma.buf.size", "1024"));
		mCmdParams.add("-t");
		mCmdParams.add(mjobConf.get("mapred.uda.log.tracelevel", "2"));

	}

	public UdaPluginRT(UdaShuffleConsumerPlugin udaShuffleConsumer, ReduceTask reduceTask, JobConf jobConf, Reporter reporter,
			int numMaps) throws IOException {
		super(jobConf);
		this.udaShuffleConsumer = udaShuffleConsumer;
		this.reduceTask = reduceTask;
		
		int maxRdmaBufferSize= jobConf.getInt("mapred.rdma.buf.size", 1024);
		int minRdmaBufferSize=jobConf.getInt("mapred.rdma.buf.size.min", 16);
		long maxHeapSize = Runtime.getRuntime().maxMemory();
		float shuffleInputBufferPercent = jobConf.getFloat("mapred.job.shuffle.input.buffer.percent", 0.7f);
		long shuffleMemorySize = (long)(maxHeapSize * shuffleInputBufferPercent);
		
		LOG.debug("UDA: numMaps=" + numMaps + 
				", maxRdmaBufferSize=" + maxRdmaBufferSize + "KB, " + 
				"minRdmaBufferSize=" + minRdmaBufferSize + "KB, " +
				"maxHeapSize=" + maxHeapSize + "B, " +
				"shuffleInputBufferPercent=" + shuffleInputBufferPercent + 
				"==> shuffleMemorySize=" + shuffleMemorySize + "B");
		
		LOG.info("UDA: user prefer rdma.buf.size=" + maxRdmaBufferSize + "KB");
		LOG.info("UDA: minimum rdma.buf.size=" + minRdmaBufferSize + "KB");

		int rdmaBufferSize=maxRdmaBufferSize * 1024; // for comparing rdmaBuffSize to shuffleMemorySize in Bytes
		if (shuffleMemorySize < numMaps * rdmaBufferSize * 2 ) { // double buffer
			rdmaBufferSize= (int)(shuffleMemorySize / (numMaps * 2) );
			//*** Can't get pagesize from java, avoid using hardcoded pagesize, c will make the alignment */ 
		
			if (rdmaBufferSize < minRdmaBufferSize * 1024) {
				throw new OutOfMemoryError("UDA: Not enough memory for rdma buffers: shuffleMemorySize=" + shuffleMemorySize + "B, mapred.rdma.buf.size.min=" + minRdmaBufferSize + "KB");
			}
			LOG.warn("UDA: Not enough memory for rdma.buf.size=" + maxRdmaBufferSize + "KB");
		}		
		LOG.info("UDA: number of segments to fetch: " + numMaps);
		LOG.info("UDA: Passing to MofSupplier rdma.buf.size=" + rdmaBufferSize + "B  (not aligned to pagesize)");
		
		/* init variables */
		init_kv_bufs(); 
		
		launchCppSide(true, this); // true: this is RT => we should execute NetMerger

		this.j2c_queue = new J2CQueue<K, V>();
		this.mTaskReporter = reporter;
		this.mMapsNeed = numMaps;


		/* send init message */
		TaskAttemptID reduceId = reduceTask.getTaskID();

		mParams.clear();
		mParams.add(Integer.toString(numMaps));
		mParams.add(reduceId.getJobID().toString());
		mParams.add(reduceId.toString());
		mParams.add(jobConf.get("mapred.netmerger.hybrid.lpq.size", "0"));
		mParams.add(Integer.toString(rdmaBufferSize)); // in Bytes
		mParams.add(Integer.toString(minRdmaBufferSize * 1024)); // in Bytes . passed for checking if rdmaBuffer is still larger than minRdmaBuffer after alignment 
		
		String [] dirs = jobConf.getLocalDirs();
		ArrayList<String> dirsCanBeCreated = new ArrayList<String>();
		//checking if the directories can be created
		for (int i=0; i<dirs.length; i++ ){
			try {
				DiskChecker.checkDir(new File(dirs[i].trim()));
				//saving only the directories that can be created
				dirsCanBeCreated.add(dirs[i].trim());
			} catch(DiskErrorException e) {  }
		}
		//sending the directories
		int numDirs = dirsCanBeCreated.size();
		mParams.add(Integer.toString(numDirs));
		for (int i=0; i<numDirs; i++ ){
			mParams.add(dirsCanBeCreated.get(i));
		}

		LOG.info("UDA: sending INIT_COMMAND");    	  
		String msg = UdaCmd.formCmd(UdaCmd.INIT_COMMAND, mParams);
		UdaBridge.doCommand(msg);
		this.mProgress = new Progress(); 
		this.mProgress.set((float)(1/2));
	}

//	public void sendFetchReq (MapOutputLocation loc) {
	public void sendFetchReq (String host, String jobID, String TaskAttemptID) {
		/* "host:jobid:mapid:reduce" */
		mParams.clear();
		mParams.add(host);
		mParams.add(jobID);
		mParams.add(TaskAttemptID);
//		mParams.add(loc.getHost());
//		mParams.add(loc.getTaskAttemptId().getJobID().toString());
//		mParams.add(loc.getTaskAttemptId().toString());
		mParams.add(Integer.toString(reduceTask.getPartition()));
		String msg = UdaCmd.formCmd(UdaCmd.FETCH_COMMAND, mParams); 
		UdaBridge.doCommand(msg);
	}

	public void close() {
		mParams.clear();
		LOG.info("UDA: sending EXIT_COMMAND");    	  
		String msg = UdaCmd.formCmd(UdaCmd.EXIT_COMMAND, mParams);
		UdaBridge.doCommand(msg);
		this.j2c_queue.close();
	}

	public <K extends Object, V extends Object>
	RawKeyValueIterator createKVIterator_rdma(JobConf job, FileSystem fs, Reporter reporter) {
		this.j2c_queue.initialize();
		return this.j2c_queue; 
	}

	// callback from C++
	public void fetchOverMessage() {
		if (LOG.isDebugEnabled()) LOG.debug(">> in fetchOverMessage"); 
		mMapsCount += mReportCount;
		if (mMapsCount >= this.mMapsNeed) mMapsCount = this.mMapsNeed;
		mTaskReporter.progress();
		if (LOG.isInfoEnabled()) LOG.info("in fetchOverMessage: mMapsCount=" + mMapsCount + " mMapsNeed=" + mMapsNeed); 

		if (mMapsCount >= this.mMapsNeed) {
			/* wake up UdaShuffleConsumerPlugin */
			if (LOG.isInfoEnabled()) LOG.info("fetchOverMessage: reached desired num of maps, waking up UdaShuffleConsumerPlugin"); 
			synchronized(udaShuffleConsumer) {
				udaShuffleConsumer.notify();
			}
		}
		if (LOG.isDebugEnabled()) LOG.debug("<< out fetchOverMessage"); 
	}


	// callback from C++
	int __cur_kv_idx__ = 0;
	public void dataFromUda(Object directBufAsObj, int len) throws Throwable {
		if (LOG.isDebugEnabled()) LOG.debug ("-->> dataFromUda len=" + len);

		KVBuf buf = kv_bufs[__cur_kv_idx__];

		synchronized (buf) {
			while (buf.status != kv_buf_recv_ready) {
				try{
					buf.wait();
				} catch (InterruptedException e) {}
			}

			buf.act_len = len;  // set merged size
			try {
				java.nio.ByteBuffer directBuf = (java.nio.ByteBuffer) directBufAsObj;
				directBuf.position(0); // reset read position 		  
				directBuf.get(buf.kv_buf, 0, len);// memcpy from direct buf into java buf - TODO: try zero-copy
			} catch (Throwable t) {
				LOG.error ("!!! !! dataFromUda GOT Exception");
				LOG.error(StringUtils.stringifyException(t));
				throw (t);
			}

			buf.kv.reset(buf.kv_buf, 0, len); // reset KV read position

			buf.status = kv_buf_redc_ready;
			++__cur_kv_idx__;
			if (__cur_kv_idx__ >= kv_buf_num) {
				__cur_kv_idx__ = 0;
			}
			buf.notifyAll();
		}
		if (LOG.isDebugEnabled()) LOG.debug ("<<-- dataFromUda finished callback");
	}


	/* kv buf object, j2c_queue uses 
 the kv object inside.
	 */
	private class KVBuf<K, V> {
		private byte[] kv_buf;
		private int act_len;
		private int status;        
		public DataInputBuffer kv;

		public KVBuf(int size) {
			kv_buf = new byte[size];
			kv = new DataInputBuffer();
			kv.reset(kv_buf,0);
			status = kv_buf_recv_ready;
		}
	}

	private class J2CQueue<K extends Object, V extends Object> 
	implements RawKeyValueIterator {  

		private int  key_len;
		private int  val_len;
		private int  cur_kv_idx;
		private int  cur_dat_len;
		private int  time_count;
		private DataInputBuffer key;
		private DataInputBuffer val;
		private DataInputBuffer cur_kv = null;

		public J2CQueue() {
			cur_kv_idx = -1;
			cur_dat_len= 0;
			key_len  = 0;
			val_len  = 0;
			key = new DataInputBuffer();
			val = new DataInputBuffer();
		} 

		private boolean move_to_next_kv() {
			if (cur_kv_idx >= 0) {
				KVBuf finished_buf = kv_bufs[cur_kv_idx];
				synchronized (finished_buf) {
					finished_buf.status = kv_buf_recv_ready;
					finished_buf.notifyAll();
				}
			}

			++cur_kv_idx;
			if (cur_kv_idx >= kv_buf_num) {
				cur_kv_idx = 0;
			}
			KVBuf next_buf = kv_bufs[cur_kv_idx];

			try { 
				synchronized (next_buf) {
					if (next_buf.status != kv_buf_redc_ready) {
						next_buf.wait();
					}
					cur_kv = next_buf.kv;
					cur_dat_len = next_buf.act_len;
					key_len = 0;
					val_len = 0;
				} 
			} catch (InterruptedException e) {
			}
			return true;
		}

		public void initialize() {
			time_count = 0;
		} 

		public DataInputBuffer getKey() {
			return key;
		}

		public DataInputBuffer getValue() {
			return val;
		}

		public boolean next() throws IOException {
			if (key_len < 0 || val_len < 0) {
				return false;
			}        

			if (cur_kv == null
					|| cur_kv.getPosition() >= (cur_dat_len - 1)) {
				move_to_next_kv(); 
			}  

			if (time_count > 1000) {
				mTaskReporter.progress();
				time_count = 0;
			}
			time_count++; 

			try {
				key_len = WritableUtils.readVInt(cur_kv);
				val_len = WritableUtils.readVInt(cur_kv); 
			} catch (java.io.EOFException e) {
				return false;
			}

			if (key_len < 0 || val_len < 0) {
				return false;
			}

			/* get key */
			this.key.reset(cur_kv.getData(), 
					cur_kv.getPosition(), 
					key_len);
			cur_kv.skip(key_len);

			//* get val
			this.val.reset(cur_kv.getData(), 
					cur_kv.getPosition(), 
					val_len);
			cur_kv.skip(val_len);

			return true;
		}

		public void close() {
			for (int i = 0; i < kv_buf_num; ++i) {
				KVBuf buf = kv_bufs[i];
				synchronized (buf) {
					buf.notifyAll();
				}
			}
		}

		public Progress getProgress() {
			return mProgress;
		}

	}
}


class UdaPluginSH extends UdaPlugin {    
	static protected final Log LOG = LogFactory.getLog(UdaPluginSH.class.getName());

	private Vector<String>     mParams       = new Vector<String>();
	private static LocalDirAllocator localDirAllocator = new LocalDirAllocator ("mapred.local.dir");
	Configuration conf;
	static IndexCache indexCache;
    private static LocalDirAllocator lDirAlloc =
        new LocalDirAllocator(YarnConfiguration.NM_LOCAL_DIRS);
	private static final Map<String,String> userRsrc = new ConcurrentHashMap<String,String>();
	
	public UdaPluginSH(Configuration conf) {
		super(new JobConf(conf));
		LOG.info("initApp of UdaPluginSH");	
		indexCache = new IndexCache(mjobConf);
		launchCppSide(false, null); // false: this is TT => we should execute MOFSupplier

	}
	
	public void addJob (String user, JobID jobId){
		userRsrc.put(jobId.toString(), user);
	}
	
	public void removeJob( JobID jobId){
		userRsrc.remove(jobId.toString());
	}
	
	
	protected void buildCmdParams() {
		mCmdParams.clear();
		
		mCmdParams.add("-w");
		mCmdParams.add(mjobConf.get("mapred.rdma.wqe.per.conn", "256"));
		mCmdParams.add("-r");
		mCmdParams.add(mjobConf.get("mapred.rdma.cma.port", "9011"));      
		mCmdParams.add("-m");
		mCmdParams.add("1");
		
		mCmdParams.add("-g");
		mCmdParams.add(System.getProperty("hadoop.log.dir"));
		
		mCmdParams.add("-s");
		mCmdParams.add(mjobConf.get("mapred.rdma.buf.size", "1024"));
		mCmdParams.add("-t");
		mCmdParams.add(mjobConf.get("mapred.uda.log.tracelevel", "2"));
	}
	

	public void close() {

		mParams.clear();
		String msg = UdaCmd.formCmd(UdaCmd.EXIT_COMMAND, mParams);
		LOG.info("UDA: sending EXIT_COMMAND");    	  
		UdaBridge.doCommand(msg);        
	}
	
	
	//this code is copied from ShuffleHandler.sendMapOutput
	static DataPassToJni getPathIndex(String jobIDStr, String mapId, int reduce){
		 String user = userRsrc.get(jobIDStr);
//		String user = "katyak";
	     DataPassToJni data = null;
	        
	     JobID jobID = JobID.forName(jobIDStr);
	     ApplicationId appID = Records.newRecord(ApplicationId.class);
	     appID.setClusterTimestamp(Long.parseLong(jobID.getJtIdentifier()));
	     appID.setId(jobID.getId());
  
	     final String base =
	         ContainerLocalizer.USERCACHE + "/" + user + "/"
	            + ContainerLocalizer.APPCACHE + "/"
	            + ConverterUtils.toString(appID) + "/output" + "/" + mapId;
	     LOG.debug("DEBUG0 " + base);
	     // Index file
	     try{
	        Path indexFileName = lDirAlloc.getLocalPathToRead(
	            base + "/file.out.index", mjobConf);
	        // Map-output file
	        Path mapOutputFileName = lDirAlloc.getLocalPathToRead(
	            base + "/file.out", mjobConf);
	        LOG.debug("DEBUG1 " + base + " : " + mapOutputFileName + " : " +
	            indexFileName);
	        IndexRecord info = 
	          indexCache.getIndexInformation(mapId, reduce, indexFileName, user);
				
		   data = new DataPassToJni();
		   data.startOffset = info.startOffset;
		   data.rawLength = info.rawLength;
		   data.partLength = info.partLength;
		   data.pathMOF = mapOutputFileName.toString();
	     }catch (IOException e){
	        	LOG.error("got an exception while retrieving the Index Info");}
	    
		return data;	
		
	}

}



class UdaCmd {

	public static final int EXIT_COMMAND        = 0; 
	public static final int NEW_MAP_COMMAND     = 1;
	public static final int FINAL_MERGE_COMMAND = 2;  
	public static final int RESULT_COMMAND      = 3;
	public static final int FETCH_COMMAND       = 4;
	public static final int FETCH_OVER_COMMAND  = 5;
	public static final int JOB_OVER_COMMAND    = 6;
	public static final int INIT_COMMAND        = 7;
	public static final int MORE_COMMAND        = 8;
	public static final int NETLEV_REDUCE_LAUNCHED = 9;
	private static final char SEPARATOR         = ':';

	/* num:cmd:param1:param2... */
	public static String formCmd(int cmd, List<String> params) {

		int size = params.size() + 1;
		String ret = "" + size + SEPARATOR + cmd;
		for (int i = 0; i < params.size(); ++i) {
			ret += SEPARATOR;
			ret += params.get(i);
		}
		return ret;
	}
}

