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
package org.apache.geode.distributed.internal.membership.gms;


import static org.apache.geode.test.awaitility.GeodeAwaitility.await;

import org.apache.geode.CancelException;
import org.apache.geode.annotations.VisibleForTesting;
import org.apache.geode.distributed.DistributedMember;
import org.apache.geode.distributed.DistributedSystem;
import org.apache.geode.distributed.internal.ClusterDistributionManager;
import org.apache.geode.distributed.internal.Distribution;
import org.apache.geode.distributed.internal.InternalDistributedSystem;
import org.apache.geode.distributed.internal.membership.InternalDistributedMember;
import org.apache.geode.distributed.internal.membership.gms.api.MembershipTestHook;
import org.apache.geode.test.awaitility.GeodeAwaitility;
import org.apache.geode.test.dunit.WaitCriterion;

/**
 * This helper class provides access to membership manager information that is not otherwise public
 *
 * @since GemFire 5.5
 */
public class MembershipManagerHelper {

  /** returns the JGroupMembershipManager for the given distributed system */
  public static Distribution getDistribution(DistributedSystem sys) {
    InternalDistributedSystem isys = (InternalDistributedSystem) sys;
    ClusterDistributionManager dm = (ClusterDistributionManager) isys.getDM();
    return dm.getDistribution();
  }

  /**
   * act sick. don't accept new connections and don't process ordered messages. Use
   * beHealthyMember() to reverse the effects.
   * <p>
   * Note that part of beSickMember's processing is to interrupt and stop any reader threads. A slow
   * listener in a reader thread should eat this interrupt.
   *
   */
  public static void beSickMember(DistributedSystem sys) {
    getDistribution(sys).beSick();
  }

  /**
   * inhibit failure detection responses. This can be used in conjunction with beSickMember
   */
  public static void playDead(DistributedSystem sys) {
    try {
      getDistribution(sys).playDead();
    } catch (CancelException e) {
      // really dead is as good as playing dead
    }
  }

  /** returns the current coordinator address */
  public static DistributedMember getCoordinator(DistributedSystem sys) {
    return getDistribution(sys).getView().getCoordinator();
  }

  /** returns the current lead member address */
  public static DistributedMember getLeadMember(DistributedSystem sys) {
    return getDistribution(sys).getView().getLeadMember();
  }

  /** register a test hook with the manager */
  public static void addTestHook(DistributedSystem sys, MembershipTestHook hook) {
    getDistribution(sys).registerTestHook(hook);
  }

  /** remove a registered test hook */
  public static void removeTestHook(DistributedSystem sys, MembershipTestHook hook) {
    getDistribution(sys).unregisterTestHook(hook);
  }

  /**
   * add a member id to the surprise members set, with the given millisecond clock birth time
   */
  public static void addSurpriseMember(DistributedSystem sys, DistributedMember mbr,
      long birthTime) {
    getDistribution(sys).addSurpriseMemberForTesting((InternalDistributedMember) mbr, birthTime);
  }

  /**
   * inhibits/enables logging of forced-disconnect messages. For quorum-lost messages this adds
   * expected-exception annotations before and after the messages to make them invisible to greplogs
   */
  public static void inhibitForcedDisconnectLogging(boolean b) {
    GMSMembership.inhibitForcedDisconnectLogging(b);
  }

  /**
   * wait for a member to leave the view. Throws an assertionerror if the timeout period elapses
   * before the member leaves
   */
  public static void waitForMemberDeparture(final DistributedSystem sys,
      final InternalDistributedMember member, final long timeout) {
    WaitCriterion ev = new WaitCriterion() {
      @Override
      public boolean done() {
        return !getDistribution(sys).getView().contains(member);
      }

      @Override
      public String description() {
        return "Waited over " + timeout + " ms for " + member + " to depart, but it didn't";
      }
    };
    GeodeAwaitility.await().untilAsserted(ev);
  }

  @VisibleForTesting
  // this method is only used for testing. Should be extract to a test helper instead
  public static void crashDistributedSystem(final DistributedSystem msys) {
    msys.getLogWriter().info("crashing distributed system: " + msys);
    Distribution mgr = ((Distribution) getDistribution(msys));
    MembershipManagerHelper.inhibitForcedDisconnectLogging(true);
    MembershipManagerHelper.beSickMember(msys);
    MembershipManagerHelper.playDead(msys);
    mgr.forceDisconnect("for testing");
    // wait at most 10 seconds for system to be disconnected
    await().until(() -> !msys.isConnected());
    MembershipManagerHelper.inhibitForcedDisconnectLogging(false);
  }

}
