
#
# Copyright (c) 2003-2009 ZeroC, Inc. All rights reserved.
#
# This copy of Ice is licensed to you under the terms described in the
# ICE_LICENSE file included in this distribution.
#
# **********************************************************************

top_srcdir	= ..\..\..

LIBNAME		= $(top_srcdir)\lib\icegridsqldb$(LIBSUFFIX).lib
DLLNAME		= $(top_srcdir)\bin\icegridsqldb$(SOVERSION)$(LIBSUFFIX).dll

TARGETS         = $(LIBNAME) $(DLLNAME)

OBJS  		= SqlStringApplicationInfoDict.obj \
		  SqlIdentityObjectInfoDict.obj \
		  SqlStringAdapterInfoDict.obj \
		  SqlDB.obj

DB_OBJS		= SqlTypes.obj

{$(top_srcdir)\src\IceDB\}.cpp.obj::
    $(CXX) /c $(CPPFLAGS) $(CXXFLAGS) $<

SRCS		= $(OBJS:.obj=.cpp) \
		  $(top_srcdir)\src\IceDB\SqlTypes.cpp

!include $(top_srcdir)\config\Make.rules.mak

CPPFLAGS	= -I..\.. -Idummyinclude $(QT_FLAGS) $(CPPFLAGS) -DWIN32_LEAN_AND_MEAN
SLICE2CPPFLAGS	= -I..\.. --ice --include-dir IceGrid\SqlDB $(SLICE2CPPFLAGS)

LINKWITH 	= $(QT_LIBS) icegrid$(LIBSUFFIX).lib glacier2$(LIBSUFFIX).lib icedb$(LIBSUFFIX).lib $(LIBS)

!if "$(GENERATE_PDB)" == "yes"
PDBFLAGS        = /pdb:$(DLLNAME:.dll=.pdb)
!endif

!if "$(BCPLUSPLUS)" == "yes"
RES_FILE        = ,, IceGridSqlDB.res
!else
RES_FILE        = IceGridSqlDB.res
!endif

$(LIBNAME): $(DLLNAME)

$(DLLNAME): $(OBJS) $(DB_OBJS) IceGridSqlDB.res
	$(LINK) $(LD_DLLFLAGS) $(PDBFLAGS) $(OBJS) $(DB_OBJS) $(PREOUT)$@ $(PRELIBS)$(LINKWITH) $(RES_FILE)
	move $(DLLNAME:.dll=.lib) $(LIBNAME)
	@if exist $@.manifest echo ^ ^ ^ Embedding manifest using $(MT) && \
	    $(MT) -nologo -manifest $@.manifest -outputresource:$@;#2 && del /q $@.manifest
	@if exist $(DLLNAME:.dll=.exp) del /q $(DLLNAME:.dll=.exp)

clean::
	-del /q IceGridSqlDB.res

install:: all
	copy $(LIBNAME) $(install_libdir)
	copy $(DLLNAME) $(install_bindir)

!include .depend
