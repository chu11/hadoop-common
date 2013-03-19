import gov.llnl.lc.chaos.*;

class MungeTest
{
    public static void main(String[] args)
    {
	try {

	    System.out.println("Creating Munge Object");
	    Munge mobj = new Munge();


	    System.out.println("Cleaning up Munge Object");
	    mobj.cleanup();
	}
	catch (Exception e) {
	    System.out.println("unexpected exception: " + e);
	}
    }
}