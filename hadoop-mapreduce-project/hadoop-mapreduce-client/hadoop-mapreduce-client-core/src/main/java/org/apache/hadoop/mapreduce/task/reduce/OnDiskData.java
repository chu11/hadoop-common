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
    private long startOffset;
    private long compressedLength;
    private long uncompressedLength;

    public OnDiskData(Path file) {
	this.file = file;
	this.reducer = -1;
        this.startOffset = -1;
        this.compressedLength = -1;
        this.uncompressedLength = -1;
    }
    
    public OnDiskData(Path file, int reducer, long startOffset,
                      long compressedLength, long uncompressedLength) {
	this.file = file;
	this.reducer = reducer;
        this.startOffset = startOffset;
        this.compressedLength = compressedLength;
        this.uncompressedLength = uncompressedLength;
    }
    public Path getPath() { return this.file; }
    public int getReducer() {return this.reducer; }
    public long getStartOffset() {return this.startOffset; }
    public long getCompressedLength() {return this.compressedLength; }
    public long getUncompressedLength() {return this.uncompressedLength; }

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
