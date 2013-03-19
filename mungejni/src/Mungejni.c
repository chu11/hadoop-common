#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include <stdint.h>
#include <unistd.h>
#include <assert.h>

#include <munge.h>

#include "Mungejni.h"

static void
_throw_munge_exception (JNIEnv *env, jobject obj, munge_err_t err)
{
  jclass munge_exception_class = NULL;
  char *exception_class_str;
  int internal_error = 0;

  switch (err)
    {
    case EMUNGE_SNAFU:
      exception_class_str = "gov/llnl/lc/chaos/MungeExceptionSnafu";
      break;
    case EMUNGE_BAD_ARG:
      exception_class_str = "gov/llnl/lc/chaos/MungeExceptionBadArg";
      break;
    case EMUNGE_BAD_LENGTH:
      exception_class_str = "gov/llnl/lc/chaos/MungeExceptionBadLength";
      break;
    case EMUNGE_OVERFLOW:
      exception_class_str = "gov/llnl/lc/chaos/MungeExceptionOverflow";
      break;
    case EMUNGE_NO_MEMORY:
      exception_class_str = "gov/llnl/lc/chaos/MungeExceptionNoMemory";
      break;
    case EMUNGE_SOCKET:
      exception_class_str = "gov/llnl/lc/chaos/MungeExceptionSocket";
      break;
    case EMUNGE_TIMEOUT:
      exception_class_str = "gov/llnl/lc/chaos/MungeExceptionTimeout";
      break;
    case EMUNGE_BAD_CRED:
      exception_class_str = "gov/llnl/lc/chaos/MungeExceptionBadCred";
      break;
    case EMUNGE_BAD_VERSION:
      exception_class_str = "gov/llnl/lc/chaos/MungeExceptionBadVersion";
      break;
    case EMUNGE_BAD_CIPHER:
      exception_class_str = "gov/llnl/lc/chaos/MungeExceptionBadCipher";
      break;
    case EMUNGE_BAD_MAC:
      exception_class_str = "gov/llnl/lc/chaos/MungeExceptionBadMac";
      break;
    case EMUNGE_BAD_ZIP:
      exception_class_str = "gov/llnl/lc/chaos/MungeExceptionBadZip";
      break;
    case EMUNGE_BAD_REALM:
      exception_class_str = "gov/llnl/lc/chaos/MungeExceptionBadRealm";
      break;
    case EMUNGE_CRED_INVALID:
      exception_class_str = "gov/llnl/lc/chaos/MungeExceptionCredInvalid";
      break;
    case EMUNGE_CRED_EXPIRED:
      exception_class_str = "gov/llnl/lc/chaos/MungeExceptionCredExpired";
      break;
    case EMUNGE_CRED_REWOUND:
      exception_class_str = "gov/llnl/lc/chaos/MungeExceptionCredRewound";
      break;
    case EMUNGE_CRED_REPLAYED:
      exception_class_str = "gov/llnl/lc/chaos/MungeExceptionCredReplayed";
      break;
    case EMUNGE_CRED_UNAUTHORIZED:
      exception_class_str = "gov/llnl/lc/chaos/MungeExceptionCredUnauthorized";
      break;
    default:
      exception_class_str = "gov/llnl/lc/chaos/MungeExceptionInternalError";
      internal_error++;
      break;
    }

  if (!(munge_exception_class = (*env)->FindClass (env, exception_class_str)))
    goto cleanup;

  if (!internal_error)
    (*env)->ThrowNew (env, munge_exception_class, munge_strerror (err));
  else
    (*env)->ThrowNew (env, munge_exception_class, "Internal Error");

 cleanup:
  (*env)->DeleteLocalRef (env, munge_exception_class);
}

JNIEXPORT jint JNICALL
Java_gov_llnl_lc_chaos_Munge_munge_1constructor (JNIEnv *env, jobject obj)
{
  munge_ctx_t munge_ctx;
  jclass munge_cls = NULL;
  jfieldID munge_ctx_addr_fid;
  jint rv = -1;

  if (!(munge_ctx = munge_ctx_create ()))
    {
      _throw_munge_exception (env, obj, EMUNGE_NO_MEMORY);
      goto cleanup;
    }

  munge_cls = (*env)->GetObjectClass (env, obj);

  if (!(munge_ctx_addr_fid = (*env)->GetFieldID (env, munge_cls, "munge_ctx_addr", "J")))
    {
      _throw_munge_exception (env, obj, -1); /* -1 is internal error */
      munge_ctx_destroy (munge_ctx);
      goto cleanup;
    }

  (*env)->SetLongField (env, obj, munge_ctx_addr_fid, (long)munge_ctx);

  rv = 0;
 cleanup:
  return (rv);

}

static int
_get_munge_ctx (JNIEnv *env, jobject obj, munge_ctx_t *munge_ctx, int throw_exception)
{
  jclass munge_cls;
  jfieldID munge_ctx_addr_fid;
  jlong jmunge_ctx_addr;
  int rv = -1;

  munge_cls = (*env)->GetObjectClass (env, obj);

  if (!(munge_ctx_addr_fid = (*env)->GetFieldID (env, munge_cls, "munge_ctx_addr", "J")))
    {
      if (throw_exception)
	_throw_munge_exception (env, obj, -1); /* -1 is internal error */
      goto cleanup;
    }

  jmunge_ctx_addr = (*env)->GetLongField (env, obj, munge_ctx_addr_fid);

  if (!jmunge_ctx_addr)
    {
      if (throw_exception)
	_throw_munge_exception (env, obj, -1); /* -1 is internal error */
      goto cleanup;
    }

  (*munge_ctx) = (munge_ctx_t)jmunge_ctx_addr;

  rv = 0;
 cleanup:
  return (rv);
}

JNIEXPORT jbyteArray JNICALL
Java_gov_llnl_lc_chaos_Munge_encode (JNIEnv *env, jobject obj, jbyteArray buf)
{
  munge_ctx_t munge_ctx;
  jsize buflen = 0;
  jbyte *userbuf = NULL;
  char *cred = NULL;
  size_t credlen = 0;
  munge_err_t err;
  jbyteArray rvcred;
  jbyteArray rv = NULL;

  if (_get_munge_ctx (env, obj, &munge_ctx, 1) < 0)
    goto cleanup;
  
  buflen = (*env)->GetArrayLength (env, buf);
  
  if (!buflen)
    {
      _throw_munge_exception (env, obj, EMUNGE_BAD_ARG);
      goto cleanup;
    }

  if (!(userbuf = (jbyte *)malloc (buflen)))
    {
      _throw_munge_exception (env, obj, EMUNGE_NO_MEMORY);
      goto cleanup;
    }

  (*env)->GetByteArrayRegion (env, buf, 0, buflen, userbuf);

  if ((err = munge_encode (&cred, munge_ctx, userbuf, buflen)) != EMUNGE_SUCCESS)
    {
      _throw_munge_exception (env, obj, err);
      goto cleanup;
    }

  /* credential is base64 NUL terminated string, so we use strlen to
   * figure out length
   */ 

  credlen = strlen (cred);

  if (!credlen)
    {
      _throw_munge_exception (env, obj, -1); /* -1 is internal error */
      goto cleanup;
    }
 
  if (!(rvcred = (*env)->NewByteArray (env, credlen)))
    {
      _throw_munge_exception (env, obj, -1); /* -1 is internal error */
      goto cleanup;
    }

  (*env)->SetByteArrayRegion (env, rvcred, 0, credlen, (jbyte *)cred);

  rv = rvcred;
 cleanup:
  free (userbuf);
  free (cred);
  return rv;
}

JNIEXPORT jobject JNICALL
Java_gov_llnl_lc_chaos_Munge_decode (JNIEnv *env, jobject obj, jbyteArray cred)
{
  munge_ctx_t munge_ctx;
  char *usercred = NULL;
  jsize credlen = 0;
  munge_err_t err;
  char *buf = NULL;
  int buflen = 0;
  uid_t uid;
  gid_t gid;
  jclass mungedecodedata_class;
  jmethodID mungedecodedata_cid;
  jbyteArray bufarray;
  jobject rvmungedecodedata;
  jobject rv = NULL;

  if (_get_munge_ctx (env, obj, &munge_ctx, 1) < 0)
    goto cleanup;
  
  credlen = (*env)->GetArrayLength (env, cred);
  
  if (!credlen)
    {
      _throw_munge_exception (env, obj, EMUNGE_BAD_ARG);
      goto cleanup;
    }

  if (!(usercred = (char *)malloc (credlen)))
    {
      _throw_munge_exception (env, obj, EMUNGE_NO_MEMORY);
      goto cleanup;
    }

  (*env)->GetByteArrayRegion (env, cred, 0, credlen, (jbyte *)usercred);

  if ((err = munge_decode (usercred,
			   munge_ctx,
			   (void **)&buf,
			   &buflen,
			   &uid,
			   &gid)) != EMUNGE_SUCCESS)
    {
      _throw_munge_exception (env, obj, err);
      goto cleanup;
    }

  if (!buf || !buflen)
    {
      _throw_munge_exception (env, obj, -1); /* -1 is internal error */
      goto cleanup;
    }
 
  if (!(mungedecodedata_class = (*env)->FindClass (env, "gov/llnl/lc/chaos/MungeDecodeData")))
    {
      _throw_munge_exception (env, obj, -1); /* -1 is internal error */
      goto cleanup;
    }

  if (!(mungedecodedata_cid = (*env)->GetMethodID (env,
						   mungedecodedata_class,
						   "<init>",
						   "([BJJ)V")))
    {
      _throw_munge_exception (env, obj, -1); /* -1 is internal error */
      goto cleanup;
    }

  if (!(bufarray = (*env)->NewByteArray (env, (jsize)buflen)))
    {
      _throw_munge_exception (env, obj, -1); /* -1 is internal error */
      goto cleanup;
    }

  (*env)->SetByteArrayRegion (env, bufarray, 0, buflen, (jbyte *)buf);

  if (!(rvmungedecodedata = (*env)->NewObject (env,
					       mungedecodedata_class,
					       mungedecodedata_cid,
					       bufarray,
					       (long)uid,
					       (long)gid)))
    {
      _throw_munge_exception (env, obj, -1); /* -1 is internal error */
      goto cleanup;
    }

  (*env)->DeleteLocalRef (env, bufarray);

  rv = rvmungedecodedata;
 cleanup:
  free (usercred);
  free (buf);
  return rv;
}

JNIEXPORT void JNICALL
Java_gov_llnl_lc_chaos_Munge_cleanup (JNIEnv *env, jobject obj)
{
  munge_ctx_t munge_ctx;
  jclass munge_cls;
  jfieldID munge_ctx_addr_fid;

  /* Don't throw exception, we're cleaning up */
  if (_get_munge_ctx (env, obj, &munge_ctx, 0) < 0)
    goto cleanup;

  munge_ctx_destroy (munge_ctx);

  munge_cls = (*env)->GetObjectClass (env, obj);

  /* Don't throw exception, we're cleaning up */
  if (!(munge_ctx_addr_fid = (*env)->GetFieldID (env, munge_cls, "munge_ctx_addr", "J")))
    goto cleanup;

  /* clear address */
  (*env)->SetLongField (env, obj, munge_ctx_addr_fid, 0);

 cleanup:
  return;
}
