##*****************************************************************************
## $Id: configure.ac,v 1.56 2010-03-24 21:32:37 chu11 Exp $
##*****************************************************************************
## Process this file with autoconf to produce a configure script.
##*****************************************************************************

##
# Prologue.
##
AC_INIT([mungejni],[1.0])
AC_META
AC_CONFIG_AUX_DIR([config])
AC_CONFIG_SRCDIR([README])
AC_CANONICAL_TARGET

##
# Automake support.
##
AM_INIT_AUTOMAKE($PROJECT, $VERSION)
AC_CONFIG_HEADERS([config/config.h])
AM_MAINTAINER_MODE

# Update this information before a public release only!

# libmungejni versioning
#
# If the library source code has changed at all since the last update,
# then increment c:r: c:r+1:).                                                                                     
#
# If any interfaces have been added, removed, or changed since the
# last update, increment current, and set revision to 0.
#
# If any interfaces have been added since the last public release,
# then increment age.
#
# If any interfaces have been removed or changed since the last public                                                
# release, then set age to 0.
#                                               

# Java JNI C library
LIBMUNGEJNI_CURRENT=0
LIBMUNGEJNI_REVISION=0
LIBMUNGEJNI_AGE=0
LIBMUNGEJNI_VERSION_INFO=$LIBMUNGEJNI_CURRENT:$LIBMUNGEJNI_REVISION:$LIBMUNGEJNI_AGE
AC_SUBST([LIBMUNGEJNI_VERSION_INFO])

##
# Checks for programs.
##
AC_PROG_CC
AC_PROG_CXX
LT_INIT
AC_PROG_MAKE_SET
AM_CONDITIONAL(WITH_GNU_LD, test "$with_gnu_ld" = "yes")
AC_PATH_PROG([JAVAC], [javac])
AC_PATH_PROG([JAVAH], [javah])
AC_PATH_PROG([JAVA], [java])
AC_PATH_PROG([JAR], [jar])
AC_PATH_PROG([JAVADOC], [javadoc])
AC_DEBUG

##
# Checks for header files.
##
AC_HEADER_STDC
AC_CHECK_HEADERS( \
  fcntl.h \
  unistd.h \
  getopt.h \
  paths.h \
)

##
# Checks for typedefs, structures, and compiler characteristics.
##
AC_C_BIGENDIAN
AC_C_CONST
AC_TYPE_UID_T

##
# Checks for library functions.
##
AC_CHECK_FUNCS( \
  strcmp \
  strlen \
  strcpy \
  strchr \
  strsep \
  strspn \
  strtok_r \
  getopt_long \
)

##
# Epilogue.
##
AC_CONFIG_FILES( \
  mungejni.spec \
  Makefile \
)
AC_OUTPUT

