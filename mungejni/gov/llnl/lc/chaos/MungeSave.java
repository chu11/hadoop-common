package gov.llnl.lc.chaos;

// Stuff I started to program, but bailed on when I realized
// it was too much work and not needed at the time.
// Just savings stuff off here
public class MungeSave
{
    // The following enums and comments are lifted pretty much
    // straight from munge.h.

    // XXX: Achu - how to do this in JNI?  Do Object casting?
    // Function per option?  Figure out later
    enum MungeOpt {
	MUNGE_OPT_CIPHER_TYPE,	   // symmetric cipher type (int)
	MUNGE_OPT_MAC_TYPE,	   // message auth code type (int)
	MUNGE_OPT_ZIP_TYPE,	   // compression type (int)
	MUNGE_OPT_REALM,	   // security realm (str)
	MUNGE_OPT_TTL,		   // time-to-live (int)
	MUNGE_OPT_ADDR4,	   // src IPv4 addr (struct in_addr)
	MUNGE_OPT_ENCODE_TIME,	   // time when cred encoded (time_t)
	MUNGE_OPT_DECODE_TIME,	   // time when cred decoded (time_t)
	MUNGE_OPT_SOCKET,	   // socket for comm w/ daemon (str)
	MUNGE_OPT_UID_RESTRICTION, // UID able to decode cred (uid_t)
	MUNGE_OPT_GID_RESTRICTION, // GID able to decode cred (gid_t)
    };

    enum MungeCipher {
	MUNGE_CIPHER_NONE,	// encryption disabled
	MUNGE_CIPHER_DEFAULT,	// default ciphr specified by daemon
	MUNGE_CIPHER_BLOWFISH,	// Blowfish CBC w/ 64b-blk/128b-key
	MUNGE_CIPHER_CAST5,	// CAST5 CBC w/ 64b-blk/128b-key
	MUNGE_CIPHER_AES128,	// AES CBC w/ 128b-blk/128b-key
	MUNGE_CIPHER_AES256,	// AES CBC w/ 128b-blk/256b-key
    };

    enum MungeMac {
	MUNGE_MAC_NONE,		// mac disabled -- invalid, btw
	MUNGE_MAC_DEFAULT,	// default mac specified by daemon
	MUNGE_MAC_MD5,		// MD5 w/ 128b-digest
	MUNGE_MAC_SHA1,		// SHA-1 w/ 160b-digest
	MUNGE_MAC_RIPEMD160,	// RIPEMD-160 w/ 160b-digest
	MUNGE_MAC_SHA256,	// SHA-256 w/ 256b-digest
	MUNGE_MAC_SHA512,	// SHA-512 w/ 512b-digest
    };

    enum MungeZip {
	MUNGE_ZIP_NONE,		// compression disabled
	MUNGE_ZIP_DEFAULT,	// default zip specified by daemon
	MUNGE_ZIP_BZLIB,	// bzip2 by Julian Seward
	MUNGE_ZIP_ZLIB,		// zlib "deflate" by Gailly & Adler
    };

    enum MungeTTL {
	MUNGE_TTL_MAXIMUM,	// maximum ttl allowed by daemon
	MUNGE_TTL_DEFAULT,	// default ttl specified by daemon
    };

    enum MungeUID {
	MUNGE_UID_ANY,		// do not restrict decode via uid
    };

    enum MungeGID {
	MUNGE_GID_ANY,		// do not restrict decode via gid
    };
}
