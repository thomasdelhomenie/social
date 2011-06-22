/*
 * Copyright (C) 2003-2011 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.social.service.rest;


import java.util.ArrayList;
import java.util.List;

import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.activity.model.ExoSocialActivityImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.manager.RelationshipManager;
import org.exoplatform.social.core.relationship.model.Relationship;
import org.exoplatform.social.core.space.SpaceException;
import org.exoplatform.social.core.space.impl.DefaultSpaceApplicationHandler;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.service.test.AbstractServiceTest;

/**
 * Unit Test for {@link SecurityManager}.
 *
 * @author <a href="http://hoatle.net">hoatle (hoatlevan at gmail dot com)</a>
 * @since Jun 17, 2011
 */
public class SecurityManagerTest extends AbstractServiceTest {

  /**
   * The identities.
   */
  private Identity rootIdentity, johnIdentity, maryIdentity, demoIdentity;

  /**
   * The identity manager.
   */
  private IdentityManager identityManager;
  /**
   * The relationship manager.
   */
  private RelationshipManager relationshipManager;
  /**
   * The activity manager.
   */
  private ActivityManager activityManager;
  /**
   * The space service.
   */
  private SpaceService spaceService;

  /**
   * The tear down list for identities.
   */
  private List<Identity> tearDownIdentityList;
  /**
   * The tear down list for activities.
   */
  private List<ExoSocialActivity> tearDownActivityList;
  /**
   * The tear down list for relationships.
   */
  private List<Relationship> tearDownRelationshipList;
  /**
   * The tear down list for spaces.
   */
  private List<Space> tearDownSpaceList;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    identityManager = (IdentityManager) getContainer().getComponentInstanceOfType(IdentityManager.class);
    relationshipManager = (RelationshipManager) getContainer().getComponentInstanceOfType(RelationshipManager.class);
    activityManager = (ActivityManager) getContainer().getComponentInstanceOfType(ActivityManager.class);
    spaceService = (SpaceService) getContainer().getComponentInstanceOfType(SpaceService.class);

    rootIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "root", false);
    johnIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "john", false);
    maryIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "mary", false);
    demoIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "demo", false);

    tearDownIdentityList = new ArrayList<Identity>();
    tearDownIdentityList.add(rootIdentity);
    tearDownIdentityList.add(johnIdentity);
    tearDownIdentityList.add(maryIdentity);
    tearDownIdentityList.add(demoIdentity);
    tearDownActivityList = new ArrayList<ExoSocialActivity>();
    tearDownRelationshipList = new ArrayList<Relationship>();
    tearDownSpaceList = new ArrayList<Space>();
  }

  @Override
  public void tearDown() throws Exception {

    for (ExoSocialActivity activity : tearDownActivityList) {
      activityManager.deleteActivity(activity);
    }

    for (Space space: tearDownSpaceList) {
      spaceService.deleteSpace(space);
    }

    for (Identity identity: tearDownIdentityList) {
      identityManager.deleteIdentity(identity);
    }

    super.tearDown();
  }


  /**
   * Tests {@link SecurityManager#canAccessActivity(PortalContainer, Identity, ExoSocialActivity)}.
   */
  public void testCanAccessActivity() {
    createActivities(demoIdentity, demoIdentity, 2);
    ExoSocialActivity activity = activityManager.getActivities(demoIdentity).get(0);
    assertTrue(SecurityManager.canAccessActivity(getContainer(), johnIdentity, activity));
  }


  /**
   * Tests {@link SecurityManager#canPostActivity(PortalContainer, Identity, Identity)}.
   */
  public void testCanPostActivity() {
    boolean demoPostToDemo = SecurityManager.canPostActivity(getContainer(), demoIdentity, demoIdentity);
    assertTrue("demoPostToDemo must be true", demoPostToDemo);
    boolean demoPostToJohn = SecurityManager.canPostActivity(getContainer(), demoIdentity, johnIdentity);
    assertFalse("demoPostToJohn must be false", demoPostToJohn);
    //demo is connected to mary => they can post activity to each other's activity stream
    relationshipManager.inviteToConnect(demoIdentity, maryIdentity);
    boolean demoPostToMary = SecurityManager.canPostActivity(getContainer(), demoIdentity, maryIdentity);
    boolean maryPostToDemo = SecurityManager.canPostActivity(getContainer(), maryIdentity, demoIdentity);
    assertFalse("demoPostToMary must be false", demoPostToMary);
    assertFalse("maryPostToDemo must be false", maryPostToDemo);
    relationshipManager.confirm(maryIdentity, demoIdentity);
    tearDownRelationshipList.add(relationshipManager.get(demoIdentity, maryIdentity));
    demoPostToMary = SecurityManager.canPostActivity(getContainer(), demoIdentity, maryIdentity);
    maryPostToDemo = SecurityManager.canPostActivity(getContainer(), maryIdentity, demoIdentity);
    assertTrue("demoPostToMary must be true", demoPostToMary);
    assertTrue("maryPostToDemo must be true", maryPostToDemo);
    //checks user posts to space
    createSpaces(1);
    Space createdSpace = tearDownSpaceList.get(0);
    Identity spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME,
                                                                 createdSpace.getPrettyName(),
                                                                 false);
    assertNotNull("spaceIdentity must not be null", spaceIdentity);

    //demo, mary could post activity to this space's activity stream

    boolean demoPostToSpace = SecurityManager.canPostActivity(getContainer(), demoIdentity, spaceIdentity);
    assertTrue("demoPostToSpace must be true", demoPostToSpace);
    boolean maryPostToSpace = SecurityManager.canPostActivity(getContainer(), maryIdentity, spaceIdentity);
    assertTrue("maryPostToSpace must be false", maryPostToSpace);

    //john could not
    boolean johnPostToSpace = SecurityManager.canPostActivity(getContainer(), johnIdentity, spaceIdentity);
    assertFalse("johnPostToSpace must be false", johnPostToSpace);
  }


  /**
   * Tests {@link SecurityManager#canDeleteActivity(PortalContainer, Identity, ExoSocialActivity)}.
   */
  public void testCanDeleteActivity() {
    createActivities(demoIdentity, demoIdentity, 2);
    ExoSocialActivity demoActivity = activityManager.getActivities(demoIdentity).get(1);
    boolean demoDeleteDemoActivity = SecurityManager.canDeleteActivity(getContainer(), demoIdentity, demoActivity);
    assertTrue("demoDeleteDemoActivity must be true", demoDeleteDemoActivity);

    boolean maryDeleteDemoActivity = SecurityManager.canDeleteActivity(getContainer(), maryIdentity, demoActivity);
    assertFalse("maryDeleteDemoActivity must be false", maryDeleteDemoActivity);


    //demo connects to john
    createActivities(johnIdentity, johnIdentity, 1);
    connectIdentities(demoIdentity, johnIdentity, false);
    ExoSocialActivity johnActivity = activityManager.getActivities(johnIdentity).get(0);
    boolean demoDeleteJohnActivity = SecurityManager.canDeleteActivity(getContainer(), demoIdentity, johnActivity);
    assertFalse("demoDeleteDemoActivity must be false", demoDeleteJohnActivity);

    connectIdentities(demoIdentity, johnIdentity, true);
    createActivities(demoIdentity, johnIdentity, 1);
    demoActivity = activityManager.getActivities(johnIdentity).get(0); //newest
    demoDeleteDemoActivity = SecurityManager.canDeleteActivity(getContainer(), demoIdentity, demoActivity);
    assertTrue("demoDeleteDemoActivity must be true", demoDeleteDemoActivity);
    boolean johnDeleteDemoActivity = SecurityManager.canDeleteActivity(getContainer(), johnIdentity, demoActivity);
    assertTrue("johnDeleteDemoActivity must be true", johnDeleteDemoActivity);

    //demo, mary, john on a space
    createSpaces(1);
    Space createdSpace = tearDownSpaceList.get(0);
    Identity spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME,
                                                                 createdSpace.getPrettyName(), false);
    tearDownIdentityList.add(spaceIdentity);
    createActivities(spaceIdentity, spaceIdentity, 1);
    ExoSocialActivity spaceActivity = activityManager.getActivities(spaceIdentity).get(0);
    boolean demoDeleteSpaceActivity = SecurityManager.canDeleteActivity(getContainer(), demoIdentity, spaceActivity);
    assertTrue("demoDeleteDemoActivity must be true", demoDeleteSpaceActivity);
    boolean maryDeleteSpaceActivity = SecurityManager.canDeleteActivity(getContainer(), maryIdentity, spaceActivity);
    assertFalse("maryDeleteSpaceActivity must be false", maryDeleteSpaceActivity);
    boolean johnDeleteSpaceActivity = SecurityManager.canDeleteActivity(getContainer(), johnIdentity, spaceActivity);
    assertFalse("johnDeleteSpaceActivity must be false", johnDeleteSpaceActivity);
    createActivities(demoIdentity, spaceIdentity, 1);
    ExoSocialActivity demoToSpaceActivity = activityManager.getActivities(spaceIdentity).get(0);
    boolean demoDeleteDemoToSpaceActivity = SecurityManager.canDeleteActivity(getContainer(),
                                                                              demoIdentity, demoToSpaceActivity);
    assertTrue("demoDeleteDemoToSpaceActivity must be true", demoDeleteDemoToSpaceActivity);
    boolean maryDeleteDemoToSpaceActivity = SecurityManager.canDeleteActivity(getContainer(),
                                                                              maryIdentity, demoToSpaceActivity);
    assertFalse("maryDeleteDemoToSpaceActivity must be false", maryDeleteDemoToSpaceActivity);
    boolean johnDeleteDemoToSpaceActivity = SecurityManager.canDeleteActivity(getContainer(),
                                                                              johnIdentity, demoToSpaceActivity);
    assertFalse("johnDeleteDemoToSpaceActivity must be false", johnDeleteDemoToSpaceActivity);
  }


  /**
   * Tests {@link SecurityManager#canCommentToActivity(PortalContainer, Identity, ExoSocialActivity)}.
   */
  public void testCanCommentToActivity() {
    createActivities(demoIdentity, demoIdentity, 1);
    ExoSocialActivity demoActivity = activityManager.getActivities(demoIdentity).get(0);
    boolean demoCommentToDemoActivity = SecurityManager.canCommentToActivity(getContainer(),
                                                                             demoIdentity, demoActivity);
    assertTrue("demoCommentToDemoActivity must be true", demoCommentToDemoActivity);

    connectIdentities(maryIdentity, demoIdentity, false);
    boolean maryCommentToDemoActivity = SecurityManager.canCommentToActivity(getContainer(),
                                                                             maryIdentity, demoActivity);
    assertFalse("maryCommentToDemoActivity must be false", maryCommentToDemoActivity);
    connectIdentities(maryIdentity, demoIdentity, true);
    maryCommentToDemoActivity = SecurityManager.canCommentToActivity(getContainer(), maryIdentity, demoActivity);
    assertTrue("maryCommentToDemoActivity must be true", maryCommentToDemoActivity);

    createSpaces(1);
    Space createdSpace = tearDownSpaceList.get(0);
    Identity spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME,
                                                                 createdSpace.getPrettyName(), false);
    tearDownIdentityList.add(spaceIdentity);
    createActivities(spaceIdentity, spaceIdentity, 1);
    ExoSocialActivity spaceActivity = activityManager.getActivities(spaceIdentity).get(0);
    boolean demoCommentToSpaceActivity = SecurityManager.canCommentToActivity(getContainer(),
                                                                              demoIdentity, spaceActivity);
    assertTrue("demoCommentToSpaceActivity must be true", demoCommentToSpaceActivity);
    boolean maryCommentToSpaceActivity = SecurityManager.canCommentToActivity(getContainer(),
                                                                              maryIdentity, spaceActivity);
    assertTrue("maryCommentToSpaceActivity must be true", maryCommentToSpaceActivity);

    boolean johnCommentToSpaceActivity = SecurityManager.canCommentToActivity(getContainer(),
                                                                              johnIdentity, spaceActivity);
    assertFalse("johnCommentToSpaceActivity must be false", johnCommentToSpaceActivity);

  }

  /**
   * Tests {@link SecurityManager#canDeleteComment(PortalContainer, Identity, ExoSocialActivity)}.
   */
  public void testCanDeleteComment() {
    createActivities(demoIdentity, demoIdentity, 1);
    ExoSocialActivity demoActivity = activityManager.getActivities(demoIdentity).get(0);
    createComment(demoActivity, demoIdentity, 1);
    ExoSocialActivity demoComment = activityManager.getComments(demoActivity).get(0);

    boolean demoDeleteDemoComment = SecurityManager.canDeleteComment(getContainer(), demoIdentity, demoComment);
    assertTrue("demoDeleteDemoComment must be true", demoDeleteDemoComment);

    // BUG #3: TODO FIX THIS
    boolean maryDeleteDemoComment = SecurityManager.canDeleteComment(getContainer(), maryIdentity, demoComment);
    assertFalse("maryDeleteDemoComment must be false", maryDeleteDemoComment);

    connectIdentities(maryIdentity, demoIdentity, true);
    createActivities(maryIdentity, demoIdentity, 1);
    ExoSocialActivity maryActivity = activityManager.getActivities(demoIdentity).get(0);
    createComment(maryActivity, demoIdentity, 1);
    createComment(maryActivity, maryIdentity, 1);
    List<ExoSocialActivity> comments = activityManager.getComments(maryActivity);
    assertEquals(2, comments.size());

    //BUG of ActivityManager, FIX IT and change these lines below following its changes.
    /*
    assertTrue("comments.get(0).getPostedTime() > comments.get(1).getPostedTime() must return true",
               comments.get(0).getPostedTime() > comments.get(1).getPostedTime());
    */
    assertTrue("comments.get(0).getPostedTime() < comments.get(1).getPostedTime() must return true",
                comments.get(0).getPostedTime() < comments.get(1).getPostedTime()); // must >
    ExoSocialActivity demoCommentMaryActivity = comments.get(0); // must be 1
    ExoSocialActivity maryCommentMaryActivity = comments.get(1); // must be 0

    boolean demoDeleteMaryCommentMaryActivity = SecurityManager.canDeleteComment(getContainer(),
                                                                                 demoIdentity, maryCommentMaryActivity);

    assertTrue("demoDeleteMaryCommentMaryActivity must be true", demoDeleteMaryCommentMaryActivity);

    boolean johnDeleteDemoCommentMaryActivity = SecurityManager.canDeleteComment(getContainer(),
                                                                                 johnIdentity,
                                                                                 demoCommentMaryActivity);
    assertFalse("johnDeleteDemoCommentMaryActivity must be false", johnDeleteDemoCommentMaryActivity);

    createSpaces(1);
    Space createdSpace = tearDownSpaceList.get(0);
    Identity spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME,
                                                                 createdSpace.getPrettyName(), false);
    createActivities(spaceIdentity, spaceIdentity, 1);
    ExoSocialActivity spaceActivity = activityManager.getActivities(spaceIdentity).get(0);

    createComment(spaceActivity, maryIdentity, 1);
    createComment(spaceActivity, demoIdentity, 1);
    List<ExoSocialActivity> spaceActivityComments = activityManager.getComments(spaceActivity);
    ExoSocialActivity maryCommentSpaceActivity = spaceActivityComments.get(0);// must be demo's comment
    ExoSocialActivity demoCommentSpaceActivity = spaceActivityComments.get(1);// must be mary's comment

    boolean maryDeleteDemoCommentSpaceActivity = SecurityManager.canDeleteComment(getContainer(),
                                                                                  maryIdentity,
                                                                                  demoCommentSpaceActivity);
    assertFalse("maryDeleteDemoCommentSpaceActivity must be false", maryDeleteDemoCommentSpaceActivity);

    boolean demoDeleteMaryCommentSpaceActivity = SecurityManager.canDeleteComment(getContainer(),
                                                                                  demoIdentity,
                                                                                  maryCommentSpaceActivity);
    assertTrue("demoDeleteMaryCommentSpaceActivity must be true", demoDeleteMaryCommentSpaceActivity);

  }

  /**
   * An identity posts an activity to an identity's activity stream with a number of activities.
   *
   * @param posterIdentity the identity who posts activity
   * @param identityStream the identity who has activity stream to be posted.
   * @param number the number of activities
   */
  private void createActivities(Identity posterIdentity, Identity identityStream, int number) {
    for (int i = 0; i < number; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setType("exosocial:core");
      activity.setTitle("title " + i);
      activity.setUserId(posterIdentity.getId());
      activity = activityManager.saveActivity(identityStream, activity);
      tearDownActivityList.add(activity);
    }
  }

  /**
   * Creates a comment to an existing activity.
   *
   * @param existingActivity the existing activity
   * @param posterIdentity the identity who comments
   * @param number the number of comments
   */
  private void createComment(ExoSocialActivity existingActivity, Identity posterIdentity, int number) {
    for (int i = 0; i < number; i++) {
      ExoSocialActivity comment = new ExoSocialActivityImpl();
      comment.setTitle("comment " + i);
      comment.setUserId(posterIdentity.getId());
      activityManager.saveComment(existingActivity, comment);
      comment = activityManager.getComments(existingActivity).get(0);
    }
  }

  /**
   * Gets an instance of the space.
   *
   * @param number the number to be created
   */
  private void createSpaces(int number) {
    for (int i = 0; i < number; i++) {
      Space space = new Space();
      space.setDisplayName("my space " + number);
      space.setRegistration(Space.OPEN);
      space.setDescription("add new space " + number);
      space.setType(DefaultSpaceApplicationHandler.NAME);
      space.setVisibility(Space.PUBLIC);
      space.setRegistration(Space.VALIDATION);
      space.setPriority(Space.INTERMEDIATE_PRIORITY);
      space.setGroupId("/space/space" + number);
      String[] managers = new String[]{"demo", "tom"};
      String[] members = new String[]{"raul", "ghost", "dragon", "demo", "mary"};
      String[] invitedUsers = new String[]{"register1", "john"};
      String[] pendingUsers = new String[]{"jame", "paul", "hacker"};
      space.setInvitedUsers(invitedUsers);
      space.setPendingUsers(pendingUsers);
      space.setManagers(managers);
      space.setMembers(members);
      try {
        spaceService.saveSpace(space, true);
        tearDownSpaceList.add(space);
      } catch (SpaceException e) {
        fail("Could not create a new space");
      }
    }
  }

  /**
   * Connects 2 identities, if toConfirm = true, they're connected. If false, in pending connection type.
   *
   * @param senderIdentity the identity who sends connection request
   * @param receiverIdentity the identity who receives connnection request
   * @param beConfirmed boolean value
   */
  private void connectIdentities(Identity senderIdentity, Identity receiverIdentity, boolean beConfirmed) {
    relationshipManager.inviteToConnect(senderIdentity, receiverIdentity);
    if (beConfirmed) {
      relationshipManager.confirm(receiverIdentity, senderIdentity);
    }
    tearDownRelationshipList.add(relationshipManager.get(senderIdentity, receiverIdentity));
  }
}