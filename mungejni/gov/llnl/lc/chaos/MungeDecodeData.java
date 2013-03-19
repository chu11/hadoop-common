package gov.llnl.lc.chaos;

/**
 * Contains data returned by a munge decode.  Most notably contains
 * buffer of data from credential, uid, and gid.
 */
public class MungeDecodeData
{
    private byte[] buf;
    // long b/c uid/gid unsigned int in C
    private long uid;
    private long gid;

    public MungeDecodeData() throws MungeException
    {
	this.buf = null;
	this.uid = 0;
	this.gid = 0;
    }

    public MungeDecodeData(byte[] buf, long uid, long gid) throws MungeException
    {
	this.buf = new byte[buf.length];
	System.arraycopy(buf, 0, this.buf, 0, buf.length);
	this.uid = uid;
	this.gid = gid;
    }

    public byte[] getbuf() { return this.buf; }
    public long getuid() { return this.uid; }
    public long getgid() { return this.gid; }
}
