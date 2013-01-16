package org.apache.hadoop.mapreduce.task.reduce;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalDirAllocator;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BoundedByteArrayOutputStream;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapOutputFile;
import org.apache.hadoop.mapreduce.TaskAttemptID;

@InterfaceAudience.LimitedPrivate({"MapReduce"})
@InterfaceStability.Unstable
public class OnDiskData {
    private Path file;
    private int reducer;
    
    public OnDiskData(Path file, int reducer) {
	this.file = file;
	this.reducer = reducer;
    }
    public Path getPath() { return this.file; }
    public int getReducer() {return this.reducer; }

    public static class OnDiskDataComparator
	implements Comparator<OnDiskData> {
	public int compare(OnDiskData o1, OnDiskData o2) {
	    String p1 = o1.getPath().toString();
	    String p2 = o2.getPath().toString();

	    // I guess any compare is ok??
	    return p1.compareTo(p2);
	}
    }
}
