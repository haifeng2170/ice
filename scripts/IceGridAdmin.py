#!/usr/bin/env python
# **********************************************************************
#
# Copyright (c) 2003-2009 ZeroC, Inc. All rights reserved.
#
# This copy of Ice is licensed to you under the terms described in the
# ICE_LICENSE file included in this distribution.
#
# **********************************************************************

import sys, os, TestUtil
from threading import Thread

#
# Set nreplicas to a number N to test replication with N replicas.
#
#nreplicas=0
nreplicas=1

iceGridPort = 12010;

nodeOptions = r' --Ice.Warn.Connections=0' + \
              r' --IceGrid.Node.Endpoints=default' + \
              r' --IceGrid.Node.WaitTime=30' + \
              r' --Ice.ProgramName=icegridnode' + \
              r' --IceGrid.Node.Trace.Replica=0' + \
              r' --IceGrid.Node.Trace.Activator=0' + \
              r' --IceGrid.Node.Trace.Adapter=0' + \
              r' --IceGrid.Node.Trace.Server=0' + \
              r' --IceGrid.Node.ThreadPool.SizeWarn=0' + \
              r' --IceGrid.Node.PrintServersReady=node' + \
              r' --Ice.NullHandleAbort' + \
              r' --Ice.ThreadPool.Server.Size=0' + \
              r' --Ice.ServerIdleTime=0'

registryOptions = r' --Ice.Warn.Connections=0' + \
                  r' --IceGrid.Registry.PermissionsVerifier=IceGrid/NullPermissionsVerifier' + \
                  r' --IceGrid.Registry.AdminPermissionsVerifier=IceGrid/NullPermissionsVerifier' + \
                  r' --IceGrid.Registry.SSLPermissionsVerifier=IceGrid/NullSSLPermissionsVerifier' + \
                  r' --IceGrid.Registry.AdminSSLPermissionsVerifier=IceGrid/NullSSLPermissionsVerifier' + \
                  r' --IceGrid.Registry.Server.Endpoints=default' + \
                  r' --IceGrid.Registry.Internal.Endpoints=default' + \
                  r' --IceGrid.Registry.SessionManager.Endpoints=default' + \
                  r' --IceGrid.Registry.AdminSessionManager.Endpoints=default' + \
                  r' --IceGrid.Registry.Trace.Session=0' + \
                  r' --IceGrid.Registry.Trace.Application=0' + \
                  r' --IceGrid.Registry.Trace.Node=0' + \
                  r' --IceGrid.Registry.Trace.Replica=0' + \
                  r' --IceGrid.Registry.Trace.Adapter=0' + \
                  r' --IceGrid.Registry.Trace.Object=0' + \
                  r' --IceGrid.Registry.Trace.Server=0' + \
                  r' --IceGrid.Registry.Trace.Locator=0' + \
                  r' --Ice.ThreadPool.Server.Size=0 ' + \
                  r' --Ice.ThreadPool.Client.SizeWarn=0' + \
                  r' --IceGrid.Registry.Client.ThreadPool.SizeWarn=0' + \
                  r' --Ice.ServerIdleTime=0' + \
                  r' --IceGrid.Registry.DefaultTemplates=' + \
                  os.path.abspath(os.path.join(TestUtil.toplevel, "cpp", "config", "templates.xml"))

def getDefaultLocatorProperty():

   i = 0
   property = '--Ice.Default.Locator="IceGrid/Locator';
   objrefs = ""
   while i < nreplicas + 1:
       objrefs = objrefs + ':default -p ' + str(iceGridPort + i)
       i = i + 1

   return ' %s%s"' % (property, objrefs)

def startIceGridRegistry(testdir, dynamicRegistration = False):

    iceGrid = ""
    if TestUtil.isNoServices():
        iceGrid = os.path.join(TestUtil.getServiceDir(), "icegridregistry")
    else:
        iceGrid = os.path.join(TestUtil.getCppBinDir(), "icegridregistry")

    command = ' --nowarn ' + registryOptions
    if dynamicRegistration:
        command += r' --IceGrid.Registry.DynamicRegistration'        

    procs = []
    i = 0
    while i < (nreplicas + 1):

        if i == 0:
            name = "registry"
        else:
            name = "replica-" + str(i)

        dataDir = os.path.join(testdir, "db", name)
        if not os.path.exists(dataDir):
            os.mkdir(dataDir)
        else:
            cleanDbDir(dataDir)

        print "starting icegrid " + name + "...",
        cmd = command + ' ' + TestUtil.getQtSqlOptions('IceGrid') + \
              r' --Ice.ProgramName=' + name + \
              r' --IceGrid.Registry.Client.Endpoints="default -p ' + str(iceGridPort + i) + '" ' + \
              r' --IceGrid.Registry.Data=' + dataDir

        if i > 0:
            cmd += r' --IceGrid.Registry.ReplicaName=' + name + ' ' + getDefaultLocatorProperty()

        driverConfig = TestUtil.DriverConfig("server")
        driverConfig.lang = "cpp"
        proc = TestUtil.startServer(iceGrid, cmd, driverConfig, count = 4)
        procs.append(proc)
        print "ok"

        i = i + 1
    return procs

def shutdownIceGridRegistry(procs):

    i = nreplicas
    while i > 0:
        print "shutting down icegrid replica-" + str(i) + "...",
        iceGridAdmin("registry shutdown replica-" + str(i))
        print "ok"
        i = i - 1

    print "shutting down icegrid registry...",
    iceGridAdmin("registry shutdown")
    print "ok"

    for p in procs:
        p.waitTestSuccess()

def startIceGridNode(testdir):

    iceGrid = ""
    if TestUtil.isNoServices():
        iceGrid = os.path.join(TestUtil.getServiceDir(), "icegridnode")
    else:
        iceGrid = os.path.join(TestUtil.getCppBinDir(), "icegridnode")

    dataDir = os.path.join(testdir, "db", "node")
    if not os.path.exists(dataDir):
        os.mkdir(dataDir)
    else:
        cleanDbDir(dataDir)

    #
    # Create property overrides from command line options.
    #
    overrideOptions = '"'
    for opt in TestUtil.getCommandLine("", TestUtil.DriverConfig("server")).split():
        opt = opt.replace("--", "")
        if opt.find("=") == -1:
            opt += "=1"
        overrideOptions += opt + " "
    overrideOptions += ' Ice.ServerIdleTime=0 Ice.PrintProcessId=0 Ice.PrintAdapterReady=0"'

    print "starting icegrid node...",
    command = r' --nowarn ' + nodeOptions + getDefaultLocatorProperty() + \
              r' --IceGrid.Node.Data=' + dataDir + \
              r' --IceGrid.Node.Name=localnode' + \
              r' --IceGrid.Node.PropertiesOverride=' + overrideOptions

    driverConfig = TestUtil.DriverConfig("server")
    driverConfig.lang = "cpp"
    proc = TestUtil.startServer(iceGrid, command, driverConfig, adapter='node')
        
    print "ok"

    return proc

def iceGridAdmin(cmd, ignoreFailure = False):

    iceGridAdmin = ""
    if TestUtil.isNoServices():
        iceGridAdmin = os.path.join(TestUtil.getServiceDir(), "icegridadmin")
    else:
        iceGridAdmin = os.path.join(TestUtil.getCppBinDir(), "icegridadmin")

    user = r"admin1"
    if cmd == "registry shutdown":
        user = r"shutdown"
    command = getDefaultLocatorProperty() + r" --IceGridAdmin.Username=" + user + " --IceGridAdmin.Password=test1 " + \
              r' -e "' + cmd + '"'

    driverConfig = TestUtil.DriverConfig("client")
    driverConfig.lang = "cpp"
    proc = TestUtil.startClient(iceGridAdmin, command, driverConfig)
    status = proc.wait()
    if not ignoreFailure and status:
        print proc.buf
        sys.exit(1)
    return proc.buf
    
def killNodeServers():
    
    for server in iceGridAdmin("server list"):
        server = server.strip()
        iceGridAdmin("server disable " + server, True)
        iceGridAdmin("server signal " + server + " SIGKILL", True)

def iceGridTest(application, additionalOptions = "", applicationOptions = ""):

    testdir = os.getcwd()
    if not TestUtil.isWin32() and os.getuid() == 0:
        print
        print "*** can't run test as root ***"
        print
        return

    if TestUtil.getDefaultMapping() == "java":
        os.environ['CLASSPATH'] = os.path.join(os.getcwd(), "classes") + os.pathsep + os.environ.get("CLASSPATH", "")

    client = TestUtil.getDefaultClientFile()
    if TestUtil.getDefaultMapping() != "java":
        client = os.path.join(testdir, client) 

    clientOptions = ' ' + getDefaultLocatorProperty() + ' ' + additionalOptions

    registryProcs = startIceGridRegistry(testdir)
    iceGridNodeProc = startIceGridNode(testdir)
    
    if application != "":
        print "adding application...",
        iceGridAdmin('application add -n ' + os.path.join(testdir, application) + ' ' + \
                     '"test.dir=' + testdir + '" "ice.bindir=' + TestUtil.getCppBinDir() + '" ' + applicationOptions)
        print "ok"

    print "starting client...",
    clientProc = TestUtil.startClient(client, clientOptions, TestUtil.DriverConfig("client"), startReader = False)
    print "ok"
    clientProc.startReader()

    clientProc.waitTestSuccess()

    if application != "":
        print "remove application...",
        iceGridAdmin("application remove Test")
        print "ok"

    print "shutting down icegrid node...",
    iceGridAdmin("node shutdown localnode")
    print "ok"
    shutdownIceGridRegistry(registryProcs)
    iceGridNodeProc.waitTestSuccess()

def iceGridClientServerTest(additionalClientOptions, additionalServerOptions):

    testdir = os.getcwd()
    server = TestUtil.getDefaultServerFile()
    client = TestUtil.getDefaultClientFile()
    if TestUtil.getDefaultMapping() != "java":
        server = os.path.join(testdir, server) 
        client = os.path.join(testdir, client) 

    if TestUtil.getDefaultMapping() == "java":
        os.environ['CLASSPATH'] = os.path.join(os.getcwd(), "classes") + os.pathsep + os.environ.get("CLASSPATH", "")

    clientOptions = getDefaultLocatorProperty() + ' ' + additionalClientOptions
    serverOptions = getDefaultLocatorProperty() + ' ' + additionalServerOptions
    
    registryProcs = startIceGridRegistry(testdir, True)

    print "starting server...",
    serverProc= TestUtil.startServer(server, serverOptions, TestUtil.DriverConfig("server"))
    print "ok"

    print "starting client...",
    clientProc = TestUtil.startClient(client, clientOptions, TestUtil.DriverConfig("client"))
    print "ok"

    clientProc.waitTestSuccess()
    serverProc.waitTestSuccess()

    shutdownIceGridRegistry(registryProcs)

def cleanDbDir(path):
    for filename in [ os.path.join(path, f) for f in os.listdir(path) if f != ".gitignore"]:
        if os.path.isdir(filename):
            cleanDbDir(filename)
            try:
                os.rmdir(filename)
            except OSError:
                # This might fail if the directory is empty (because
                # it itself contains a .gitignore file.
                pass
        else:
            os.remove(filename)
