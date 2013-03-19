import gov.llnl.lc.chaos.*;

class MungeTest
{
    public static void main(String[] args)
    {
	try {

	    System.out.println("Creating Munge Object");
	    Munge mobj = new Munge();

	    String str = "YouCanCallMeAl";

	    byte[] strbytes = str.getBytes();

	    System.out.println("Original String: " + str);

	    System.out.println("Encoding ...");
	    byte[] encodedbytes = null;

	    try {
		encodedbytes = mobj.encode(strbytes);
	    }
	    catch (MungeException e) {
		System.out.println("encode exception: " + e);
		System.exit(1);
	    }

	    String encodedstr = new String(encodedbytes);

	    System.out.println("Encoded string: " + encodedstr);

	    System.out.println("Decoding ...");
	    MungeDecodeData mdata = null;

	    try {
		mdata = mobj.decode(encodedbytes);
	    }
	    catch (MungeException e) {
		System.out.println("decode exception: " + e);
		System.exit(1);
	    }

	    String decodedstr = new String(mdata.getbuf());

	    System.out.println("decode buf: " + decodedstr);
	    System.out.println("decode uid: " + mdata.getuid());
	    System.out.println("decode gid: " + mdata.getgid());

	    System.out.println("Cleaning up Munge Object");
	    mobj.cleanup();
	}
	catch (Exception e) {
	    System.out.println("unexpected exception: " + e);
	    System.exit(1);
	}

	System.exit(0);
    }
}