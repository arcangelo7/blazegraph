/*

 Copyright (C) SYSTAP, LLC 2006-2008.  All rights reserved.

 Contact:
 SYSTAP, LLC
 4501 Tower Road
 Greensboro, NC 27410
 licenses@bigdata.com

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; version 2 of the License.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

 */
/*
 * Created on Jul 10, 2008
 */

package com.bigdata.relation;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.apache.log4j.Logger;

import com.bigdata.journal.IIndexManager;
import com.bigdata.journal.IResourceLock;
import com.bigdata.journal.IResourceLockService;
import com.bigdata.relation.locator.DefaultResourceLocator;
import com.bigdata.relation.locator.ILocatableResource;
import com.bigdata.relation.locator.RelationSchema;
import com.bigdata.service.IBigdataFederation;

/**
 * Base class for locatable resources.
 * 
 * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan Thompson</a>
 * @version $Id$
 * 
 * @param <E>
 */
abstract public class AbstractResource<E> implements IMutableResource<E>{

    protected final static Logger log = Logger.getLogger(AbstractResource.class);
    
    protected final static boolean INFO = log.isInfoEnabled();

    protected final static boolean DEBUG = log.isDebugEnabled();

    final private IIndexManager indexManager;
    
    final private String namespace;

    final private String containerNamespace;

    final private long timestamp;
    
    final private Properties properties;
    
    /**
     * 
     */
    protected AbstractResource(final IIndexManager indexManager,
            final String namespace, final Long timestamp,
            final Properties properties) {

        if (indexManager == null)
            throw new IllegalArgumentException();

        if (namespace == null)
            throw new IllegalArgumentException();

        if (timestamp == null)
            throw new IllegalArgumentException();

        if (properties == null)
            throw new IllegalArgumentException();

        this.indexManager = indexManager;

        this.namespace = namespace;

        {
            String val = properties.getProperty(RelationSchema.CONTAINER);

            this.containerNamespace = val;
        
        }
        
        this.timestamp = timestamp;

        this.properties = properties;
        
        properties.setProperty(RelationSchema.NAMESPACE, namespace);

        properties.setProperty(RelationSchema.CLASS, getClass().getName());
        
        if (INFO) {

            log.info("namespace=" + namespace + ", timestamp=" + timestamp
                    + ", container=" + containerNamespace + ", indexManager="
                    + indexManager);
            
        }
        
    }
    
    public final String getNamespace() {
        
        return namespace;
        
    }

    public final String getContainerNamespace() {
        
        return containerNamespace;
        
    }

    /**
     * Return the container.
     * 
     * @return The container -or- <code>null</code> if there is no container.
     */
    public ILocatableResource getContainer() {

        if (container == null) {

            synchronized (this) {

                if (container == null) {

                    if (getContainerNamespace() != null) {

                        if (INFO) {

                            log.info("resolving container: "
                                    + getContainerNamespace());

                        }

                        container = getIndexManager()
                                .getResourceLocator()
                                .locate(getContainerNamespace(), getTimestamp());

                    }

                }

            }
            
        }

        return container;
        
    }
    private volatile ILocatableResource container;
    
    public final long getTimestamp() {
        
        return timestamp;
        
    }

    /**
     * Return an object wrapping the properties specified to the ctor.
     */
    public final Properties getProperties() {
        
        return new Properties(properties);
        
    }

    /**
     * Return the object used to locate indices, relations, and relation
     * containers and to execute operations on those resources.
     * <p> 
     * Note: Return type SHOULD be strengthen by concrete impls.
     * 
     * @return The {@link IIndexManager}.
     */
    public IIndexManager getIndexManager() {
        
        return indexManager;
        
    }
    
    final public ExecutorService getExecutorService() {
        
        return indexManager.getExecutorService();
        
    }
    
    /**
     * The class name, timestamp and namespace for the relation view.
     */
    public String toString() {
        
        return getClass().getSimpleName() + "{timestamp=" + timestamp
                + ", namespace=" + namespace + ", container=" + containerNamespace
                + ", indexManager=" + indexManager + "}";
    
    }

    /**
     * 
     * @todo Lock service supporting shared locks, leases and lease renewal,
     *       excalation of shared locks to exclusive locks, deadlock detection,
     *       and possibly a resource hierarchy. Leases should be Callable
     *       objects that are submitted by the client to its executor service so
     *       that they will renew automatically until cancelled (and will cancel
     *       automatically if not renewed).
     *       <P>
     *       There is existing code that could be adapted for this purpose. It
     *       might have to be adapted to support lock escalation (shared to
     *       exclusive), a resource hierarchy, and a delay queue to cancel
     *       leases that are not renewed. It would have to be wrapped up as a
     *       low-latency service and made available via the
     *       {@link IBigdataFederation}. It also needs to use a weak reference
     *       cache for the collection of resource queues so that they are GC'd
     *       rather than growing as new resources are locked and never
     *       shrinking.
     *       <p>
     *       If we require pre-declaration of locks, then we do not need the
     *       dependency graph since deadlocks can only arise with 2PL.
     *       <p>
     *       Since the service is remote it should use {@link UUID}s to
     *       identify the lock owner(s).
     *       <p>
     *       The lock service would be used to bracket operations such as
     *       relation {@link #create()} and {@link #destroy()} and would be used
     *       to prevent those operations while a lease is held by concurrent
     *       processes with a shared lock.
     *       <p>
     *       Add ctor flag to create iff not found?
     *       <p>
     *       There needs to be a lock protocol for subclasses so that they can
     *       ensure that they are the only task running create (across the
     *       federation) and so that they can release the lock when they are
     *       done. The lock can be per the notes above, but the protocol with
     *       the subclass will require some coordinating methods.
     *       <p>
     *       Full transactions are another way to solve this problem.
     */
    public void create() {
        
        if (log.isInfoEnabled())
            log.info(toString());
    
        /*
         * Convert the Properties to a Map.
         */
        final Map<String, Object> map = new HashMap<String, Object>();
        {

            final Enumeration<? extends Object> e = properties.propertyNames();

            while (e.hasMoreElements()) {

                final Object key = e.nextElement();

//                if (!(key instanceof String)) {
//
//                    log.warn("Will not store non-String key: " + key);
//
//                    continue;
//
//                }

                final String name = (String) key;

                map.put(name, properties.getProperty(name));

            }

        }
    
        // Write the map on the row store.
        final Map afterMap = indexManager.getGlobalRowStore().write(
                RelationSchema.INSTANCE, map);
        
        if(DEBUG) {
            
            log.debug("Properties after write: "+afterMap);
            
        }
        
        /*
         * Add this instance to the locator cache.
         * 
         * Note: Normally, the instances are created by the locator cache
         * itself. In general the only the the application creates an instance
         * directly is when it is going to attempt to create the relation. This
         * takes advantage of that pattern to notify the locator that it should
         * cache this instance.
         */
        
        ((DefaultResourceLocator)getIndexManager().getResourceLocator()).putInstance(this);
        
    }

    public void destroy() {
    
        if (INFO)
            log.info(toString());
    
        // Delete the entry for this relation from the row store.
        indexManager.getGlobalRowStore().delete(RelationSchema.INSTANCE, namespace);
        
    }

    /**
     * Acquires an exclusive lock for the {@link #getNamespace()}.
     * 
     * @return the lock.
     * 
     * @throws RuntimeException if anything goes wrong.
     * 
     * @see IResourceLockService
     */
    protected IResourceLock acquireExclusiveLock() {

        final int maxtries = 3;
        
        int ntries = 0;
        
        final long millis = 10;
        
        IResourceLockService lockService;
        
        while ((lockService = indexManager.getResourceLockService()) == null
                && ntries < maxtries) {
            
            if(INFO) {
                
                log.info("Will retry");
                
            }
            
            ntries++;
            
            try {
                
                Thread.sleep(millis);
                
            } catch (InterruptedException ex) {
             
                throw new RuntimeException(ex);
                
            }
            
        }
        
        if (lockService == null) {
            
            throw new RuntimeException("Could not locate lock service");
            
        }
        
        try {

            return lockService.acquireExclusiveLock(getNamespace());

        } catch (IOException ex) {

            throw new RuntimeException(ex);

        }

    }

    /**
     * Release the lock.
     * 
     * @param resourceLock
     *            The lock.
     */
    protected void unlock(IResourceLock resourceLock) {
        
        try {
            
            resourceLock.unlock();
            
        } catch (IOException e) {
            
            throw new RuntimeException(e);
            
        }
        
    }
    
}
