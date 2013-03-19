package gov.llnl.lc.chaos;

/**
 * Java JNI extensions wrapper around libmunge C library.
 */
public class Munge
{
    private long munge_ctx_addr = 0;

    private native int munge_constructor();

    /**
     * Creates a Munge object
     */
    public Munge() throws MungeException
    {
	munge_constructor();
    }

    /**
     * Creates a credential from the specified buffer
     */
    public native byte[] encode(byte[] buf) throws MungeException;

    /**
     * Decodes a credential
     */
    public native MungeDecodeData decode(byte[] cred) throws MungeException;

    /**
     * Cleans up allocated memory.  Must be called to free memory from
     * underlying calls.  After this method is called, all munge
     * methods above cannot be called and will result in errors.
     */ 
    public native void cleanup();

    static
    {
	System.loadLibrary("Mungejni");
    }
}
