##*****************************************************************************
## $Id: Makefile.am,v 1.14 2007-10-17 17:31:14 chu11 Exp $
##*****************************************************************************
## Process this file with automake to produce Makefile.in.
##*****************************************************************************

MUNGE_JAVA_FILES = \
	$(srcdir)/gov/llnl/lc/chaos/Munge.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeDecodeData.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeException.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeExceptionSnafu.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeExceptionBadArg.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeExceptionBadLength.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeExceptionOverflow.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeExceptionNoMemory.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeExceptionSocket.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeExceptionTimeout.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeExceptionBadCred.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeExceptionBadVersion.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeExceptionBadCipher.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeExceptionBadMac.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeExceptionBadZip.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeExceptionBadRealm.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeExceptionCredInvalid.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeExceptionCredExpired.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeExceptionCredRewound.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeExceptionCredReplayed.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeExceptionCredUnauthorized.java \
	$(srcdir)/gov/llnl/lc/chaos/MungeExceptionInternalError.java


noinst_HEADERS  = src/Mungejni.h
lib_LTLIBRARIES = src/libMungejni.la

src_libMungejni_la_CXXFLAGS = -D_REENTRANT \
			      -I $(srcdir)/../../libmunge/

src_libMungejni_la_SOURCES = src/Mungejni.c

src_libMungejni_la_LDFLAGS = -lmunge -version-info @LIBMUNGEJNI_VERSION_INFO@ $(OTHER_FLAGS)

all: $(MUNGE_JAVA_FILES)
	$(JAVAC) $(MUNGE_JAVA_FILES)
	$(JAR) cvf $(builddir)/gov/llnl/lc/chaos/Munge.jar $(builddir)/gov/llnl/lc/chaos/*.class
	$(JAVADOC) -d javadoc $(MUNGE_JAVA_FILES)

munge_header: all
	$(JAVAH) -o src/Mungejni.h gov.llnl.lc.chaos.Munge

install-data-local: all
	$(INSTALL) -m 755 -d $(DESTDIR)$(datadir)/java
	$(INSTALL) -m 644 $(builddir)/gov/llnl/lc/chaos/Munge.jar $(DESTDIR)$(datadir)/java
	$(INSTALL) -m 755 -d $(DESTDIR)$(docdir)-$(VERSION)-javadoc/
# achu: Not sure of recursive install command, is there one?
	cp -r javadoc/* $(DESTDIR)$(docdir)-$(VERSION)-javadoc

uninstall:
	rm -f $(DESTDIR)$(datadir)/java/Munge.jar
	rm -rf $(DESTDIR)$(docdir)-$(VERSION)-javadoc/*

test:
	$(JAVAC) -classpath . MungeTest/MungeTest.java
	$(JAVA) -Djava.library.path=src/.libs -classpath ".:MungeTest" MungeTest

clean-local:
	rm -f gov/llnl/lc/chaos/*.class
	rm -f gov/llnl/lc/chaos/*.jar
	rm -f MungeTest/*.class
	rm -rf javadoc/*

EXTRA_DIST = \
	$(MUNGE_JAVA_FILES) \
	MungeTest/MungeTest.java \
	gov/llnl/lc/chaos/MANIFEST.MF \
	META \
	mungejni.spec
