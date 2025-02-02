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

import static org.apache.geode.distributed.ConfigurationProperties.ACK_SEVERE_ALERT_THRESHOLD;
import static org.apache.geode.distributed.ConfigurationProperties.ACK_WAIT_THRESHOLD;
import static org.apache.geode.distributed.ConfigurationProperties.DISABLE_TCP;
import static org.apache.geode.distributed.ConfigurationProperties.LOCATORS;
import static org.apache.geode.distributed.ConfigurationProperties.LOG_FILE;
import static org.apache.geode.distributed.ConfigurationProperties.LOG_LEVEL;
import static org.apache.geode.distributed.ConfigurationProperties.MCAST_PORT;
import static org.apache.geode.distributed.ConfigurationProperties.MCAST_TTL;
import static org.apache.geode.distributed.ConfigurationProperties.MEMBER_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.stream.Collectors;

import org.jgroups.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

import org.apache.geode.distributed.internal.ClusterDistributionManager;
import org.apache.geode.distributed.internal.DistributionConfig;
import org.apache.geode.distributed.internal.DistributionConfigImpl;
import org.apache.geode.distributed.internal.DistributionMessage;
import org.apache.geode.distributed.internal.HighPriorityAckedMessage;
import org.apache.geode.distributed.internal.membership.InternalDistributedMember;
import org.apache.geode.distributed.internal.membership.adapter.LocalViewMessage;
import org.apache.geode.distributed.internal.membership.adapter.ServiceConfig;
import org.apache.geode.distributed.internal.membership.gms.GMSMembership.StartupEvent;
import org.apache.geode.distributed.internal.membership.gms.Services.Stopper;
import org.apache.geode.distributed.internal.membership.gms.api.Authenticator;
import org.apache.geode.distributed.internal.membership.gms.api.LifecycleListener;
import org.apache.geode.distributed.internal.membership.gms.api.MemberIdentifier;
import org.apache.geode.distributed.internal.membership.gms.api.MembershipConfig;
import org.apache.geode.distributed.internal.membership.gms.api.MembershipListener;
import org.apache.geode.distributed.internal.membership.gms.api.MembershipView;
import org.apache.geode.distributed.internal.membership.gms.api.Message;
import org.apache.geode.distributed.internal.membership.gms.api.MessageListener;
import org.apache.geode.distributed.internal.membership.gms.interfaces.HealthMonitor;
import org.apache.geode.distributed.internal.membership.gms.interfaces.JoinLeave;
import org.apache.geode.distributed.internal.membership.gms.interfaces.Messenger;
import org.apache.geode.internal.admin.remote.RemoteTransportConfig;
import org.apache.geode.test.junit.categories.MembershipTest;

@Category({MembershipTest.class})
public class GMSMembershipJUnitTest {



  private Services services;
  private MembershipConfig mockConfig;
  private DistributionConfig distConfig;
  private Authenticator authenticator;
  private HealthMonitor healthMonitor;
  private InternalDistributedMember myMemberId;
  private InternalDistributedMember[] mockMembers;
  private Messenger messenger;
  private JoinLeave joinLeave;
  private Stopper stopper;
  private MembershipListener listener;
  private GMSMembership<InternalDistributedMember> manager;
  private List<InternalDistributedMember> members;
  private MessageListener messageListener;
  private LifecycleListener directChannelCallback;

  @Before
  public void initMocks() throws Exception {
    Properties nonDefault = new Properties();
    nonDefault.put(ACK_WAIT_THRESHOLD, "1");
    nonDefault.put(ACK_SEVERE_ALERT_THRESHOLD, "10");
    nonDefault.put(DISABLE_TCP, "true");
    nonDefault.put(MCAST_PORT, "0");
    nonDefault.put(MCAST_TTL, "0");
    nonDefault.put(LOG_FILE, "");
    nonDefault.put(LOG_LEVEL, "fine");
    nonDefault.put(MEMBER_TIMEOUT, "2000");
    nonDefault.put(LOCATORS, "localhost[10344]");
    distConfig = new DistributionConfigImpl(nonDefault);
    RemoteTransportConfig tconfig =
        new RemoteTransportConfig(distConfig, ClusterDistributionManager.NORMAL_DM_TYPE);

    mockConfig = new ServiceConfig(tconfig, distConfig);

    authenticator = mock(Authenticator.class);
    myMemberId = new InternalDistributedMember("localhost", 8887);
    GMSMemberData m = (GMSMemberData) myMemberId.getMemberData();
    UUID uuid = new UUID(12345, 12345);
    m.setUUID(uuid);

    messenger = mock(Messenger.class);
    when(messenger.getMemberID()).thenReturn(myMemberId);

    stopper = mock(Stopper.class);
    when(stopper.isCancelInProgress()).thenReturn(false);

    healthMonitor = mock(HealthMonitor.class);
    when(healthMonitor.getFailureDetectionPort()).thenReturn(Integer.valueOf(-1));

    joinLeave = mock(JoinLeave.class);

    services = mock(Services.class);
    when(services.getAuthenticator()).thenReturn(authenticator);
    when(services.getConfig()).thenReturn(mockConfig);
    when(services.getMessenger()).thenReturn(messenger);
    when(services.getCancelCriterion()).thenReturn(stopper);
    when(services.getHealthMonitor()).thenReturn(healthMonitor);
    when(services.getJoinLeave()).thenReturn(joinLeave);

    Timer t = new Timer(true);
    when(services.getTimer()).thenReturn(t);

    Random r = new Random();
    mockMembers = new InternalDistributedMember[5];
    for (int i = 0; i < mockMembers.length; i++) {
      mockMembers[i] = new InternalDistributedMember("localhost", 8888 + i);
      m = (GMSMemberData) mockMembers[i].getMemberData();
      uuid = new UUID(r.nextLong(), r.nextLong());
      m.setUUID(uuid);
    }
    members = new ArrayList<>(Arrays.asList(mockMembers));

    listener = mock(MembershipListener.class);
    messageListener = mock(MessageListener.class);
    directChannelCallback = mock(LifecycleListener.class);
    manager = new GMSMembership(listener, messageListener, directChannelCallback);
    manager.getGMSManager().init(services);
    when(services.getManager()).thenReturn(manager.getGMSManager());
  }

  @After
  public void tearDown() throws Exception {
    if (manager != null) {
      manager.getGMSManager().stop();
      manager.getGMSManager().stopped();
    }
  }

  @Test
  public void testSendMessage() throws Exception {
    HighPriorityAckedMessage m = new HighPriorityAckedMessage();
    m.setRecipient(mockMembers[0]);
    manager.getGMSManager().start();
    manager.getGMSManager().started();
    MemberIdentifier myGMSMemberId = myMemberId;
    List<MemberIdentifier> gmsMembers =
        members.stream().map(x -> ((MemberIdentifier) x)).collect(Collectors.toList());
    manager.getGMSManager().installView(new GMSMembershipView(myGMSMemberId, 1, gmsMembers));
    Set<InternalDistributedMember> failures =
        manager.send(m.getRecipientsArray(), m);
    verify(messenger).send(isA(Message.class));
    if (failures != null) {
      assertEquals(0, failures.size());
    }
  }



  private GMSMembershipView createView(InternalDistributedMember creator, int viewId,
      List<InternalDistributedMember> members) {
    List<MemberIdentifier> gmsMembers = new ArrayList<>(members);
    return new GMSMembershipView(creator, viewId, gmsMembers);
  }

  @Test
  public void testStartupEvents() throws Exception {
    manager.getGMSManager().start();
    manager.getGMSManager().started();
    manager.isJoining = true;

    List<InternalDistributedMember> viewmembers =
        Arrays.asList(new InternalDistributedMember[] {mockMembers[0], myMemberId});
    manager.getGMSManager().installView(createView(myMemberId, 2, viewmembers));

    // add a surprise member that will be shunned due to it's having
    // an old view ID
    InternalDistributedMember surpriseMember = mockMembers[2];
    surpriseMember.setVmViewId(1);
    manager.handleOrDeferSurpriseConnect(surpriseMember);
    assertEquals(1, manager.getStartupEvents().size());

    // add a surprise member that will be accepted
    InternalDistributedMember surpriseMember2 = mockMembers[3];
    surpriseMember2.setVmViewId(3);
    manager.handleOrDeferSurpriseConnect(surpriseMember2);
    assertEquals(2, manager.getStartupEvents().size());

    // suspect a member
    InternalDistributedMember suspectMember = mockMembers[1];
    manager.handleOrDeferSuspect(
        new SuspectMember(mockMembers[0], suspectMember, "testing"));
    // suspect messages aren't queued - they're ignored before joining the system
    assertEquals(2, manager.getStartupEvents().size());
    verify(listener, never()).memberSuspect(suspectMember, mockMembers[0], "testing");

    HighPriorityAckedMessage m = new HighPriorityAckedMessage();
    mockMembers[0].setVmViewId(1);
    m.setRecipient(mockMembers[0]);
    m.setSender(mockMembers[1]);
    manager.handleOrDeferMessage(m);
    assertEquals(3, manager.getStartupEvents().size());

    // this view officially adds surpriseMember2
    viewmembers = Arrays
        .asList(new InternalDistributedMember[] {mockMembers[0], myMemberId, surpriseMember2});
    manager.handleOrDeferViewEvent(new MembershipView(myMemberId, 3, viewmembers));
    assertEquals(4, manager.getStartupEvents().size());

    // add a surprise member that will be shunned due to it's having
    // an old view ID
    InternalDistributedMember surpriseMember3 = mockMembers[4];
    surpriseMember.setVmViewId(1);
    manager.handleOrDeferSurpriseConnect(surpriseMember);
    assertEquals(5, manager.getStartupEvents().size());

    // process a new view after we finish joining but before event processing has started
    manager.isJoining = false;
    mockMembers[4].setVmViewId(4);
    viewmembers = Arrays.asList(new InternalDistributedMember[] {mockMembers[0], myMemberId,
        surpriseMember2, mockMembers[4]});
    manager.handleOrDeferViewEvent(new MembershipView(myMemberId, 4, viewmembers));
    assertEquals(6, manager.getStartupEvents().size());

    // exercise the toString methods for code coverage
    for (StartupEvent ev : manager.getStartupEvents()) {
      ev.toString();
    }

    manager.startEventProcessing();

    // all startup events should have been processed
    assertEquals(0, manager.getStartupEvents().size());
    // the new view should have been installed
    assertEquals(4, manager.getView().getViewId());
    // supriseMember2 should have been announced
    verify(listener).newMemberConnected(surpriseMember2);
    // supriseMember should have been rejected (old view ID)
    verify(listener, never()).newMemberConnected(surpriseMember);

    // for code coverage also install a view after we finish joining but before
    // event processing has started. This should notify the distribution manager
    // with a LocalViewMessage to process the view
    reset(listener);
    manager.handleOrDeferViewEvent(new MembershipView(myMemberId, 5, viewmembers));
    assertEquals(0, manager.getStartupEvents().size());
    verify(messageListener).messageReceived(isA(LocalViewMessage.class));

    // process a suspect now - it will be passed to the listener
    reset(listener);
    suspectMember = mockMembers[1];
    manager.handleOrDeferSuspect(
        new SuspectMember(mockMembers[0], suspectMember, "testing"));
    verify(listener).memberSuspect(suspectMember, mockMembers[0], "testing");
  }

  /**
   * This test ensures that the membership manager can accept an ID that does not have a UUID and
   * replace it with one that does have a UUID from the current membership view.
   */
  @Test
  public void testAddressesWithoutUUIDs() throws Exception {
    manager.getGMSManager().start();
    manager.getGMSManager().started();
    manager.isJoining = true;

    List<InternalDistributedMember> viewmembers =
        Arrays.asList(new InternalDistributedMember[] {mockMembers[0], mockMembers[1], myMemberId});
    GMSMembershipView view = createView(myMemberId, 2, viewmembers);
    manager.getGMSManager().installView(view);
    when(services.getJoinLeave().getView()).thenReturn(view);

    InternalDistributedMember[] destinations = new InternalDistributedMember[viewmembers.size()];
    for (int i = 0; i < destinations.length; i++) {
      InternalDistributedMember id = viewmembers.get(i);
      destinations[i] = new InternalDistributedMember(id.getHost(), id.getMembershipPort());
    }
    manager.checkAddressesForUUIDs(destinations);
    // each destination w/o a UUID should have been replaced with the corresponding
    // ID from the membership view
    for (int i = 0; i < destinations.length; i++) {
      assertTrue(((GMSMemberData) destinations[i].getMemberData()).hasUUID());
    }
  }

  @Test
  public void noDispatchWhenSick() {
    final DistributionMessage msg = mock(DistributionMessage.class);
    when(msg.dropMessageWhenMembershipIsPlayingDead()).thenReturn(true);

    final GMSMembership spy = Mockito.spy(manager);

    spy.beSick();
    spy.getGMSManager().start();
    spy.getGMSManager().started();

    spy.handleOrDeferMessage(msg);

    verify(spy, never()).dispatchMessage(any(DistributionMessage.class));
    assertThat(spy.getStartupEvents()).isEmpty();
  }

}
