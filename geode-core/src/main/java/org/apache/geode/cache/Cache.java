/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.cache;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.geode.admin.AdminDistributedSystem;
import org.apache.geode.cache.asyncqueue.AsyncEventQueue;
import org.apache.geode.cache.asyncqueue.AsyncEventQueueFactory;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.server.CacheServer;
import org.apache.geode.cache.snapshot.CacheSnapshotService;
import org.apache.geode.cache.util.GatewayConflictResolver;
import org.apache.geode.cache.wan.GatewayReceiver;
import org.apache.geode.cache.wan.GatewayReceiverFactory;
import org.apache.geode.cache.wan.GatewaySender;
import org.apache.geode.cache.wan.GatewaySenderFactory;
import org.apache.geode.distributed.DistributedMember;
import org.apache.geode.distributed.DistributedSystem;
import org.apache.geode.distributed.LeaseExpiredException;
import org.apache.geode.i18n.LogWriterI18n;

/**
 * Caches are obtained from the {@link CacheFactory#create()} method. See {@link CacheFactory} for
 * common usage patterns for creating the cache instance.
 *
 * <p>
 * When a cache is created a {@link DistributedSystem} is also created. This system tells the cache
 * where to find other caches on the network and how to communicate with them. The system can also
 * specify a <a href="../distribution/DistributedSystem.html#cache-xml-file">"cache-xml-file"</a>
 * property which will cause this cache to be initialized with the contents of that file. The
 * contents must comply with the {@code "doc-files/cache8_0.dtd"} file and the top level element
 * must be a {@code cache} element.
 *
 * <p>
 * When a cache will no longer be used it should be {@link #close() closed}. Once it
 * {@link #isClosed is closed} any attempt to use it or any {@link Region} obtained from it will
 * cause a {@link CacheClosedException} to be thrown.
 *
 * <p>
 * A cache can have multiple root regions, each with a different name.
 *
 * @since GemFire 2.0
 */
@SuppressWarnings("deprecation")
public interface Cache extends GemFireCache {

  /**
   * Terminates this object cache and releases all the resources. Calls {@link Region#close} on each
   * region in the cache. After this cache is closed, any further method call on this cache or any
   * region object will throw {@link CacheClosedException}, unless otherwise noted.
   *
   * @param keepAlive whether the server should keep the durable client's queues alive for the
   *        timeout period
   * @throws CacheClosedException if the cache is already closed.
   * @deprecated as of 6.5 use {@link ClientCache#close(boolean)} instead.
   */
  @Deprecated
  void close(boolean keepAlive);

  /**
   * Creates a VM region using the specified RegionAttributes.
   *
   * @param name the name of the region to create
   * @param aRegionAttributes the attributes of the root region
   * @return the region object
   * @throws RegionExistsException if a region is already in this cache
   * @throws LeaseExpiredException if lease expired on distributed lock for Scope.GLOBAL
   * @throws TimeoutException if timed out getting distributed lock for Scope.GLOBAL
   * @throws CacheClosedException if the cache is closed
   * @throws IllegalStateException If the supplied RegionAttributes violate the
   *         <a href="AttributesFactory.html#creationConstraints">region creation constraints</a>
   *         with a region of the same name in another cache in the distributed system
   * @deprecated as of GemFire 5.0, use {@link #createRegion} instead.
   */
  @Deprecated
  <K, V> Region<K, V> createVMRegion(String name, RegionAttributes<K, V> aRegionAttributes)
      throws RegionExistsException, TimeoutException;

  /**
   * Creates a region using the specified RegionAttributes.
   *
   * @param name the name of the region to create
   * @param aRegionAttributes the attributes of the root region
   * @return the region object
   * @throws RegionExistsException if a region is already in this cache
   * @throws LeaseExpiredException if lease expired on distributed lock for Scope.GLOBAL
   * @throws TimeoutException if timed out getting distributed lock for Scope.GLOBAL
   * @throws CacheClosedException if the cache is closed
   * @throws IllegalStateException If the supplied RegionAttributes violate the
   *         <a href="AttributesFactory.html#creationConstraints">region creation constraints</a>
   *         with a region of the same name in another cache in the distributed system
   * @since GemFire 5.0
   * @deprecated as of 6.5 use {@link #createRegionFactory(RegionAttributes)} instead
   */
  @Deprecated
  <K, V> Region<K, V> createRegion(String name, RegionAttributes<K, V> aRegionAttributes)
      throws RegionExistsException, TimeoutException;

  /**
   * Creates a {@link RegionFactory} which can be used to specify additional attributes for
   * {@link Region} creation.
   *
   * @see #createRegionFactory(RegionShortcut)
   * @since GemFire 6.5
   */
  <K, V> RegionFactory<K, V> createRegionFactory();

  /**
   * Creates a {@link RegionFactory} for the most commonly used {@link Region} types defined by
   * {@link RegionShortcut}
   *
   * @since GemFire 6.5
   */
  <K, V> RegionFactory<K, V> createRegionFactory(RegionShortcut shortcut);

  /**
   * Creates a {@link RegionFactory} for creating a {@link Region} from {@link RegionAttributes}
   * mapped to this regionAttributesId
   *
   * @param regionAttributesId the Id of RegionAttributes to be used
   * @see #setRegionAttributes(String, RegionAttributes)
   * @since GemFire 6.5
   */
  <K, V> RegionFactory<K, V> createRegionFactory(String regionAttributesId);

  /**
   * Creates a {@link RegionFactory} for creating a {@link Region} from the given regionAttributes
   *
   * @param regionAttributes regionAttributes for the new region
   * @see #createRegionFactory(RegionShortcut)
   * @since GemFire 6.5
   */
  <K, V> RegionFactory<K, V> createRegionFactory(RegionAttributes<K, V> regionAttributes);

  /**
   * Internal GemStone method for accessing the internationalized logging object for GemFire, use
   * {@link #getLogger()} instead. This method does not throw {@code CacheClosedException} if the
   * cache is closed.
   *
   * @return the logging object
   * @deprecated as of 6.5 use getLogger().convertToLogWriterI18n() instead
   */
  @Deprecated
  LogWriterI18n getLoggerI18n();

  /**
   * Internal GemStone method for accessing the internationalized logging object for GemFire, use
   * {@link #getSecurityLogger()} instead. This method does not throw {@code CacheClosedException}
   * if the cache is closed.
   *
   * @return the security logging object
   * @deprecated as of 6.5 use getSecurityLogger().convertToLogWriterI18n() instead
   */
  @Deprecated
  LogWriterI18n getSecurityLoggerI18n();

  /**
   * Gets the number of seconds a cache operation will wait to obtain a distributed lock lease. This
   * method does not throw {@code CacheClosedException} if the cache is closed.
   */
  int getLockTimeout();

  /**
   * Sets the number of seconds a cache operation may wait to obtain a distributed lock lease before
   * timing out.
   *
   * @throws IllegalArgumentException if {@code seconds} is less than zero
   */
  void setLockTimeout(int seconds);

  /**
   * Gets the frequency (in seconds) at which a message will be sent by the primary cache-server to
   * all the secondary cache-server nodes to remove the events which have already been dispatched
   * from the queue.
   *
   * @return The time interval in seconds
   */
  int getMessageSyncInterval();

  /**
   * Sets the frequency (in seconds) at which a message will be sent by the primary cache-server
   * node to all the secondary cache-server nodes to remove the events which have already been
   * dispatched from the queue.
   *
   * @param seconds the time interval in seconds
   * @throws IllegalArgumentException if {@code seconds} is less than zero
   */
  void setMessageSyncInterval(int seconds);

  /**
   * Gets the length, in seconds, of distributed lock leases obtained by this cache. This method
   * does not throw {@code CacheClosedException} if the cache is closed.
   */
  int getLockLease();

  /**
   * Sets the length, in seconds, of distributed lock leases obtained by this cache.
   *
   * @throws IllegalArgumentException if {@code seconds} is less than zero.
   */
  void setLockLease(int seconds);

  /**
   * Gets the number of seconds a cache {@link Region#get(Object) get}
   * operation can spend searching for a value before it times out. The search includes any time
   * spent loading the object. When the search times out it causes the get to fail by throwing an
   * exception. This method does not throw {@code CacheClosedException} if the cache is closed.
   */
  int getSearchTimeout();

  /**
   * Sets the number of seconds a cache get operation can spend searching for a value.
   *
   * @throws IllegalArgumentException if {@code seconds} is less than zero
   */
  void setSearchTimeout(int seconds);

  /**
   * Creates a new cache server, with the default configuration, that will allow clients to access
   * this cache.
   *
   * <p>
   * For the default configuration see the constants in
   * {@link CacheServer}.
   *
   * @see CacheServer
   *
   * @since GemFire 5.7
   */
  CacheServer addCacheServer();

  /**
   * Returns a collection of all of the {@code CacheServer}s that can serve the contents of this
   * {@code Cache} to clients.
   *
   * @see #addCacheServer
   *
   * @since GemFire 5.7
   */
  List<CacheServer> getCacheServers();

  /**
   * Adds a gateway event conflict resolution resolver. This is invoked if an event is processed
   * that comes from a different distributed system than the last event to modify the affected
   * entry. It may alter the event or disallow the event. If it does neither the event is applied to
   * the cache if its timestamp is newer than what is in the cache or if it is the same and the
   * event's distributed system ID is larger than that of the last event to modify the affected
   * entry.
   *
   * @param resolver gateway event conflict resolution resolver
   * @since GemFire 7.0
   */
  void setGatewayConflictResolver(GatewayConflictResolver resolver);

  /**
   * Returns the current gateway event conflict resolver
   *
   * @since GemFire 7.0
   */
  GatewayConflictResolver getGatewayConflictResolver();

  /**
   * Sets whether or not this {@code Cache} resides in a long-running "cache server" VM. A cache
   * server may be an application VM or may be a stand-along VM launched using
   * {@linkplain AdminDistributedSystem#addCacheServer administration API} or
   * the {@code cacheserver} command line utility.
   *
   * @since GemFire 4.0
   */
  void setIsServer(boolean isServer);

  /**
   * Returns whether or not this cache resides in a "cache server" VM.
   *
   * @see #setIsServer
   *
   * @since GemFire 4.0
   */
  boolean isServer();

  /**
   * Notifies the server that this client is ready to receive updates. This method is used by
   * durable clients to notify servers that they are ready to receive updates. As soon as the server
   * receives this message, it will forward updates to this client (if necessary).
   * <p>
   * Durable clients must call this method after they are done creating regions. If it is called
   * before the client creates the regions then updates will be lost. Any time a new {@link Pool} is
   * created and regions have been added to it then this method needs to be called again.
   * <p>
   *
   * @throws IllegalStateException if called by a non-durable client
   *
   * @since GemFire 5.5
   * @deprecated as of 6.5 use {@link ClientCache#readyForEvents} instead.
   */
  @Deprecated
  void readyForEvents();

  /**
   * Creates {@link GatewaySenderFactory} for creating a SerialGatewaySender
   *
   * @since GemFire 7.0
   */
  @Override
  GatewaySenderFactory createGatewaySenderFactory();

  /**
   * Creates {@link AsyncEventQueueFactory} for creating a AsyncEventQueue
   *
   * @since GemFire 7.0
   */
  AsyncEventQueueFactory createAsyncEventQueueFactory();

  /**
   * Creates {@link GatewayReceiverFactory} for creating a GatewayReceiver
   *
   * @since GemFire 7.0
   */
  GatewayReceiverFactory createGatewayReceiverFactory();

  /**
   * Returns all {@link GatewaySender}s for this Cache.
   *
   * @return Set of GatewaySenders
   * @since GemFire 7.0
   */
  Set<GatewaySender> getGatewaySenders();

  /**
   * Returns the {@link GatewaySender} with the given id added to this Cache.
   *
   * @return GatewaySender with id
   * @since GemFire 7.0
   */
  GatewaySender getGatewaySender(String id);

  /**
   * Returns all {@link GatewayReceiver}s for this Cache
   *
   * @return Set of GatewaySenders
   * @since GemFire 7.0
   */
  Set<GatewayReceiver> getGatewayReceivers();

  /**
   * Returns all {@link AsyncEventQueue}s for this Cache
   *
   * @return Set of AsyncEventQueue
   * @since GemFire 7.0
   */
  Set<AsyncEventQueue> getAsyncEventQueues();

  /**
   * Returns the {@link AsyncEventQueue} with the given id added to this Cache.
   *
   * @return AsyncEventQueue with id
   * @since GemFire 7.0
   */
  AsyncEventQueue getAsyncEventQueue(String id);

  /**
   * Returns a set of the other non-administrative members in the distributed system.
   *
   * @since GemFire 6.6
   */
  Set<DistributedMember> getMembers();

  /**
   * Returns a set of the administrative members in the distributed system.
   *
   * @since GemFire 6.6
   */
  Set<DistributedMember> getAdminMembers();

  /**
   * Returns a set of the members in the distributed system that have the given region. For regions
   * with local scope an empty set is returned.
   *
   * @param region a Region in the cache
   * @since GemFire 6.6
   */
  Set<DistributedMember> getMembers(Region region);

  /**
   * Obtains the snapshot service to allow the cache data to be imported or exported.
   *
   * @return the snapshot service
   */
  CacheSnapshotService getSnapshotService();

  /**
   * Test to see whether the Cache is in the process of reconnecting and recreating a new cache
   * after it has been removed from the system by other members or has shut down due to missing
   * Roles and is reconnecting.
   * <p>
   * This will also return true if the Cache has finished reconnecting. When reconnect has completed
   * you can use {@link Cache#getReconnectedCache} to retrieve the new cache instance.
   *
   * @return true if the Cache is attempting to reconnect or has finished reconnecting
   */
  boolean isReconnecting();

  /**
   * Wait for the Cache to finish reconnecting to the distributed system and recreate a new Cache.
   * This may throw a CacheClosedException if reconnect attempts fail due to an exception. The
   * exception will detail what went wrong.
   *
   * @see #getReconnectedCache
   * @param time amount of time to wait, or -1 to wait forever
   * @param units time unit
   * @return true if the cache was reconnected
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  boolean waitUntilReconnected(long time, TimeUnit units) throws InterruptedException;

  /**
   * Force the Cache to stop reconnecting. If the Cache is currently connected this will disconnect
   * and close it.
   *
   */
  void stopReconnecting();

  /**
   * Returns the new Cache if there was an auto-reconnect and the cache was recreated.
   */
  Cache getReconnectedCache();
}
