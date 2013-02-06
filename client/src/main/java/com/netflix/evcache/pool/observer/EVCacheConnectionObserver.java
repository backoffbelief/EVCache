package com.netflix.evcache.pool.observer;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import net.spy.memcached.ConnectionObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;

public class EVCacheConnectionObserver implements ConnectionObserver, EVCacheConnectionObserverMBean {

	private static final Logger log = LoggerFactory.getLogger(EVCacheConnectionObserver.class);
	
	/*
	 * The name of the client host
	 */
    private String hostName;
    
    /*
     * The name of the EVCache app. 
     */
    private final String appName;
    
    /*
     * The zone of the client host
     */
    private final String zone;
    
    /*
     * The id of the client 
     */
    private final int id;

    /*
     * The EVCache hosts that this clients has an active connection
     */
    private final HashSet<SocketAddress> evCacheActiveSet;

    /*
     * The EVCache hosts that this clients has lost the connection
     */
    private final HashSet<SocketAddress> evCacheInActiveSet;

    /*
     * The EVCache hosts this client has an active connection and the time the connection was established
     */
    private final HashMap<String, Long> evCacheActiveStringSet;

    /*
     * The EVCache hosts this client has lost the connection to and the time the connection was lost
     */
    private final HashMap<String, Long> evCacheInActiveStringSet;

    public EVCacheConnectionObserver(String appName) {
    	this(appName, 0);
    }

    public EVCacheConnectionObserver(String appName, int id) {
    	this(appName, "GLOBAL", id);
    }

    public EVCacheConnectionObserver(String appName, String zone, int id) {
    	try {
			this.hostName = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			this.hostName = "NA";
		}
        this.appName = appName;
        this.zone = zone;
        this.evCacheActiveSet = new HashSet<SocketAddress>();
        this.evCacheInActiveSet = new HashSet<SocketAddress>();
        this.evCacheActiveStringSet = new HashMap<String, Long>();
        this.evCacheInActiveStringSet = new HashMap<String, Long>();
        this.id = id;
        setupMonitoring(false);
    }

    /**
     * {@inheritDoc}
     */
    public void connectionEstablished(SocketAddress sa, int reconnectCount) {		
        final String address = sa.toString();
        evCacheActiveSet.add(sa);
        evCacheInActiveSet.remove(sa);
        final String hostName = ((InetSocketAddress)sa).getHostName();
        evCacheActiveStringSet.put(hostName, Long.valueOf(System.currentTimeMillis()));
        evCacheInActiveStringSet.remove(hostName);
        if(log.isInfoEnabled()) log.info(appName + ":CONNECTION ESTABLISHED : From " + hostName + " to " + address + " was established after " + reconnectCount + " retries");
    }

    /**
     * {@inheritDoc}
     */
    public void connectionLost(SocketAddress sa) {
        final String address = sa.toString();
        evCacheInActiveSet.add(sa);
        evCacheActiveSet.remove(sa);
        final String hostName = ((InetSocketAddress)sa).getHostName();
        evCacheInActiveStringSet.put(hostName, Long.valueOf(System.currentTimeMillis()));
        evCacheActiveStringSet.remove(hostName);
        if(log.isInfoEnabled()) log.info(appName + ":CONNECTION LOST : From " + hostName + " to " + address );
    }

    @Monitor(name="ActiveServerCount", type=DataSourceType.GAUGE)
    public int getActiveServerCount() {
        return evCacheActiveSet.size();
    }

    @Monitor(name="ActiveServerNames", type=DataSourceType.INFORMATIONAL)
    public Set<SocketAddress> getActiveServerNames() {
        return evCacheActiveSet;
    }

    @Monitor(name="InActiveServerCount", type=DataSourceType.GAUGE)
    public int getInActiveServerCount() {
        return evCacheInActiveSet.size();
    }

    @Monitor(name="InActiveServerNames", type=DataSourceType.INFORMATIONAL)
    public Set<SocketAddress> getInActiveServerNames() {
        return evCacheInActiveSet;
    }

    public Map<String, Long> getInActiveServerInfo() {
        return evCacheInActiveStringSet;
    }

    public Map<String, Long> getActiveServerInfo() {
        return evCacheActiveStringSet;
    }

    private void setupMonitoring(boolean shutdown) {
        try {
            final ObjectName mBeanName = ObjectName.getInstance("com.netflix.evcache:Group="+ appName + ",SubGroup=pool,SubSubGroup="+zone + ",SubSubSubGroup="+id);
            final MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
            if(mbeanServer.isRegistered(mBeanName)) {
                if(log.isInfoEnabled()) log.info("MBEAN with name " + mBeanName + " has been registered. Will unregister the previous instance and register a new one.");
                mbeanServer.unregisterMBean(mBeanName);
            }
            if(!shutdown) {
                mbeanServer.registerMBean(this, mBeanName);
            } 
        } catch (Exception e) {
            if(log.isWarnEnabled()) log.warn("Issue while trying to setup monitoring", e);
        }
    }

    /**
     * Shutdown this connection observer. This will also remove the monitoring.  
     */
    public void shutdown() {
        setupMonitoring(true);
    }

    public String toString() {
        return "EVCacheConnectionObserver [host=" + hostName
                + ", appName=" + appName + ", zone=" + zone + ", id=" + id
                + ", evCacheActiveSet=" + evCacheActiveSet
                + ", evCacheInActiveSet=" + evCacheInActiveSet
                + ", evCacheActiveStringSet=" + evCacheActiveStringSet
                + ", evCacheInActiveStringSet=" + evCacheInActiveStringSet
                + "]";
    }

}