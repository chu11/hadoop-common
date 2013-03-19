package gov.llnl.lc.chaos;

/**
 * Munge Exception class, parent to all specific Munge Exceptions.
 */
public class MungeException extends Exception 
{
    public MungeException(String msg)
    {
	super(msg);
    }
}
