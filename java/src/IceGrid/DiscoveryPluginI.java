// **********************************************************************
//
// Copyright (c) 2003-2014 ZeroC, Inc. All rights reserved.
//
// This copy of Ice is licensed to you under the terms described in the
// ICE_LICENSE file included in this distribution.
//
// **********************************************************************

package IceGrid;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

class DiscoveryPluginI implements Ice.Plugin
{
    private static class Request
    {
        Request(LocatorI locator, 
                String operation, 
                Ice.OperationMode mode, 
                byte[] inParams,
                java.util.Map<String, String> context,
                Ice.AMD_Object_ice_invoke amdCB)
        {
            _locator = locator;
            _operation = operation;
            _mode = mode;
            _inParams = inParams;
            _context = context;
            _amdCB = amdCB;
        }

        void
        invoke(Ice.LocatorPrx l)
        {
            _locatorPrx = l;
            l.begin_ice_invoke(_operation, _mode, _inParams, _context, 
                new Ice.Callback_Object_ice_invoke()
                {
                    public void
                    response(boolean ok, byte[] outParams)
                    {
                        _amdCB.ice_response(ok, outParams);
                    }

                    public void
                    exception(Ice.LocalException ex)
                    {
                        _locator.invoke(_locatorPrx, Request.this); // Retry with new locator proxy
                    }
                    
                    public void
                    exception(Ice.SystemException ex)
                    {
                        _locator.invoke(_locatorPrx, Request.this); // Retry with new locator proxy
                    }
                });
        }

        private final LocatorI _locator;
        private final String _operation;
        private final Ice.OperationMode _mode;
        private final java.util.Map<String, String> _context;
        private final byte[] _inParams;
        private final Ice.AMD_Object_ice_invoke _amdCB;

        private Ice.LocatorPrx _locatorPrx;
    };

    static private class VoidLocatorI extends IceGrid._LocatorDisp
    {
        public void 
        findObjectById_async(Ice.AMD_Locator_findObjectById amdCB, Ice.Identity id, Ice.Current current)
        {
            amdCB.ice_response(null);
        }
        
        public void 
        findAdapterById_async(Ice.AMD_Locator_findAdapterById amdCB, String id, Ice.Current current)
        {
            amdCB.ice_response(null);
        }
        
        public Ice.LocatorRegistryPrx 
        getRegistry(Ice.Current current)
        {
            return null;
        }
        
        public IceGrid.RegistryPrx 
        getLocalRegistry(Ice.Current current)
        {
            return null;
        }
        
        public IceGrid.QueryPrx 
        getLocalQuery(Ice.Current current)
        {
            return null;
        }
    };

    private static class LocatorI extends Ice.BlobjectAsync
    {
        LocatorI(LookupPrx lookup, Ice.Properties properties, String instanceName, IceGrid.LocatorPrx voidLocator)
        {
            _lookup = lookup;
            _timeout = properties.getPropertyAsIntWithDefault("IceGridDiscovery.Timeout", 300);
            _retryCount = properties.getPropertyAsIntWithDefault("IceGridDiscovery.RetryCount", 3);
            _retryDelay = properties.getPropertyAsIntWithDefault("IceGridDiscovery.RetryDelay", 2000);
            _timer = IceInternal.Util.getInstance(lookup.ice_getCommunicator()).timer();
            _instanceName = instanceName;
            _warned = false;
            _locator = lookup.ice_getCommunicator().getDefaultLocator();
            _voidLocator = voidLocator;
            _pendingRetryCount = 0;
        }

        public void
        setLookupReply(LookupReplyPrx lookupReply)
        {
            _lookupReply = lookupReply;
        }

        public synchronized void
        ice_invoke_async(Ice.AMD_Object_ice_invoke amdCB, byte[] inParams, Ice.Current current)
        {
            invoke(null, new Request(this, current.operation, current.mode, inParams, current.ctx, amdCB));
        }

        public synchronized void
        foundLocator(LocatorPrx locator)
        {
            if(locator == null || 
               (!_instanceName.isEmpty() && !locator.ice_getIdentity().category.equals(_instanceName)))
            {
                return;
            }

            //
            // If we already have a locator assigned, ensure the given locator
            // has the same identity, otherwise ignore it.
            //
            if(_locator != null && !locator.ice_getIdentity().category.equals(_locator.ice_getIdentity().category))
            {
                if(!_warned)
                {
                    _warned = true; // Only warn once

                    locator.ice_getCommunicator().getLogger().warning(
                        "received IceGrid locator with different instance name:\n" +
                        "using = `" + _locator.ice_getIdentity().category + "'\n" +
                        "received = `" + locator.ice_getIdentity().category + "'\n" +
                        "This is typically the case if multiple IceGrid registries with different " +
                        "nstance names are deployed and the property `IceGridDiscovery.InstanceName'" +
                        "is not set.");
                }
                return;
            }

            if(_pendingRetryCount > 0) // No need to retry, we found a locator
            {
                _future.cancel(false);
                _future = null;

                _pendingRetryCount = 0;
            }

            if(_locator != null)
            {
                //
                // We found another locator replica, append its endpoints to the
                // current locator proxy endpoints.
                //
                List<Ice.Endpoint> newEndpoints = new ArrayList<Ice.Endpoint>(
                    Arrays.asList(_locator.ice_getEndpoints()));
                for(Ice.Endpoint p : locator.ice_getEndpoints())
                {
                    //
                    // Only add endpoints if not already in the locator proxy endpoints
                    //
                    boolean found = false;
                    for(Ice.Endpoint q : newEndpoints)
                    {
                        if(p.equals(q))
                        {
                            found = true;
                            break;
                        }
                    }
                    if(!found)
                    {
                        newEndpoints.add(p);
                    }

                }
                _locator = (LocatorPrx)_locator.ice_endpoints(
                    newEndpoints.toArray(new Ice.Endpoint[newEndpoints.size()]));
            }
            else
            {
                _locator = locator;
                if(_instanceName.isEmpty())
                {
                    _instanceName = _locator.ice_getIdentity().category; // Stick to the first locator
                }
            }

            //
            // Send pending requests if any.
            //
            for(Request req : _pendingRequests)
            {
                req.invoke(_locator);
            }
            _pendingRequests.clear();
        }


        public synchronized void
        invoke(Ice.LocatorPrx locator, Request request)
        {
            if(_locator != null && _locator != locator)
            {
                request.invoke(_locator);
            }
            else if(IceInternal.Time.currentMonotonicTimeMillis() < _nextRetry)
            {
                request.invoke(_voidLocator); // Don't retry to find a locator before the retry delay expires
            }
            else
            {
                _locator = null;

                _pendingRequests.add(request);
                
                if(_pendingRetryCount == 0) // No request in progress
                {
                    _pendingRetryCount = _retryCount;
                    _lookup.begin_findLocator(_instanceName, _lookupReply); // Send multicast request.
                    _future = _timer.schedule(_retryTask, _timeout, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            }
        }

        private Runnable _retryTask = new Runnable()
        {
            public void run()
            {
                synchronized(LocatorI.this)
                {
                    if(--_pendingRetryCount > 0)
                    {
                        _lookup.begin_findLocator(_instanceName, _lookupReply); // Send multicast request.
                        _future = _timer.schedule(_retryTask, _timeout, java.util.concurrent.TimeUnit.MILLISECONDS);
                    }
                    else
                    {
                        assert !_pendingRequests.isEmpty();
                        for(Request req : _pendingRequests)
                        {
                            req.invoke(_voidLocator);
                        }
                        _pendingRequests.clear();
                        _nextRetry = IceInternal.Time.currentMonotonicTimeMillis() + _retryDelay;
                    }
                }

            }
        };

        private final LookupPrx _lookup;
        private final int _timeout;
        private java.util.concurrent.Future<?> _future;
        private final java.util.concurrent.ScheduledExecutorService _timer;
        private final int _retryCount;
        private final int _retryDelay;

        private String _instanceName;
        private boolean _warned;
        private LookupReplyPrx _lookupReply;
        private Ice.LocatorPrx _locator;
        private Ice.LocatorPrx _voidLocator;

        private int _pendingRetryCount;
        private List<Request> _pendingRequests = new ArrayList<Request>();
        private long _nextRetry;
    };

    private class LookupReplyI extends _LookupReplyDisp
    {
        LookupReplyI(LocatorI locator)
        {
            _locator = locator;
        }

        public void
        foundLocator(LocatorPrx locator, Ice.Current curr)
        {
            _locator.foundLocator(locator);
        }

        private final LocatorI _locator;
    };

    public
    DiscoveryPluginI(Ice.Communicator communicator)
    {
        _communicator = communicator;
    }

    public void
    initialize()
    {
        Ice.Properties properties = _communicator.getProperties();

        boolean ipv4 = properties.getPropertyAsIntWithDefault("Ice.IPv4", 1) > 0;
        String address;
        if(ipv4)
        {
            address = properties.getPropertyWithDefault("IceGridDiscovery.Address", "239.255.0.1");
        }
        else
        {
            address = properties.getPropertyWithDefault("IceGridDiscovery.Address", "ff15::1");
        }
        int port = properties.getPropertyAsIntWithDefault("IceGridDiscovery.Port", 4061);
        String intf = properties.getProperty("IceGridDiscovery.Interface");

        if(properties.getProperty("IceGridDiscovery.Reply.Endpoints").isEmpty())
        {
            StringBuilder s = new StringBuilder();
            s.append("udp");
            if(!intf.isEmpty())
            {
                s.append(" -h \"").append(intf).append("\"");
            }
            properties.setProperty("IceGridDiscovery.Reply.Endpoints", s.toString());
        }
        if(properties.getProperty("IceGridDiscovery.Locator.Endpoints").isEmpty())
        {
            properties.setProperty("IceGridDiscovery.Locator.AdapterId", java.util.UUID.randomUUID().toString());
        }

        _replyAdapter = _communicator.createObjectAdapter("IceGridDiscovery.Reply");
        _locatorAdapter = _communicator.createObjectAdapter("IceGridDiscovery.Locator");

        // We don't want those adapters to be registered with the locator so clear their locator.
        _replyAdapter.setLocator(null);
        _locatorAdapter.setLocator(null);

        String lookupEndpoints = properties.getProperty("IceGridDiscovery.Lookup");
        if(lookupEndpoints.isEmpty())
        {
            StringBuilder s = new StringBuilder();
            s.append("udp -h \"").append(address).append("\" -p ").append(port);
            if(!intf.isEmpty())
            {
                s.append(" --interface \"").append(intf).append("\"");
            }
            lookupEndpoints = s.toString();
        }

        Ice.ObjectPrx lookupPrx = _communicator.stringToProxy("IceGrid/Lookup -d:" + lookupEndpoints);
        lookupPrx = lookupPrx.ice_collocationOptimized(false); // No collocation optimization for the multicast proxy!
        try
        {
            lookupPrx.ice_getConnection(); // Ensure we can establish a connection to the multicast proxy
        }
        catch(Ice.LocalException ex)
        {
            StringBuilder s = new StringBuilder();
            s.append("unable to establish multicast connection, IceGrid discovery will be disabled:\n");
            s.append("proxy = ").append(lookupPrx.toString()).append("\n").append(ex);
            throw new Ice.PluginInitializationException(s.toString());
        }

        LocatorPrx voidLoc = LocatorPrxHelper.uncheckedCast(_locatorAdapter.addWithUUID(new VoidLocatorI()));
        
        String instanceName = properties.getProperty("IceGridDiscovery.InstanceName");
        Ice.Identity id = new Ice.Identity();
        id.name = "Locator";
        id.category = !instanceName.isEmpty() ? instanceName : java.util.UUID.randomUUID().toString();
        LocatorI locator = new LocatorI(LookupPrxHelper.uncheckedCast(lookupPrx), properties, instanceName, voidLoc);
        _communicator.setDefaultLocator(Ice.LocatorPrxHelper.uncheckedCast(_locatorAdapter.addWithUUID(locator)));

        Ice.ObjectPrx lookupReply = _replyAdapter.addWithUUID(new LookupReplyI(locator)).ice_datagram();
        locator.setLookupReply(LookupReplyPrxHelper.uncheckedCast(lookupReply));

        _replyAdapter.activate();
        _locatorAdapter.activate();
    }

    public void
    destroy()
    {
        _replyAdapter.destroy();
        _locatorAdapter.destroy();
    }

    private Ice.Communicator _communicator;
    private Ice.ObjectAdapter _locatorAdapter;
    private Ice.ObjectAdapter _replyAdapter;
}