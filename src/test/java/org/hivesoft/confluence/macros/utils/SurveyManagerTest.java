package org.hivesoft.confluence.macros.utils;

import com.atlassian.confluence.core.ContentEntityObject;
import com.atlassian.confluence.core.ContentPropertyManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.renderer.v2.macro.MacroException;
import com.atlassian.user.impl.DefaultUser;
import org.hivesoft.confluence.macros.survey.model.Survey;
import org.hivesoft.confluence.macros.vote.model.Ballot;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SurveyManagerTest {
  private final static DefaultUser SOME_USER1 = new DefaultUser("someUser1", "someUser1 FullName", "some1@testmail.de");

  ContentPropertyManager mockContentPropertyManager = mock(ContentPropertyManager.class);

  SurveyManager classUnderTest;

  @Before
  public void setup() {
    classUnderTest = new SurveyManager(mockContentPropertyManager);
  }

  @Test
  public void test_reconstructBallot_noChoices_success() throws MacroException {
    final Ballot reconstructedBallot = classUnderTest.reconstructBallot("someTitle", "", new Page());

    assertEquals("someTitle", reconstructedBallot.getTitle());
  }

  @Test
  public void test_reconstructBallot_someChoices_noVoter_success() throws MacroException {
    String someChoices = "someChoice1\r\nsomeChoice2";

    final Ballot reconstructedBallot = classUnderTest.reconstructBallot("someTitle", someChoices, new Page());

    assertEquals("someTitle", reconstructedBallot.getTitle());
    assertEquals("someChoice1", reconstructedBallot.getChoice("someChoice1").getDescription());
    assertEquals("someChoice2", reconstructedBallot.getChoice("someChoice2").getDescription());
    assertFalse(reconstructedBallot.getChoice("someChoice1").getHasVotedFor(SOME_USER1.getName()));
  }

  @Test
  public void test_reconstructBallot_someChoices_withVoter_success() throws MacroException {
    String someChoices = "someChoice1\r\nsomeChoice2";

    when(mockContentPropertyManager.getTextProperty(any(ContentEntityObject.class), anyString())).thenReturn(SOME_USER1.getName());
    final Ballot reconstructedBallot = classUnderTest.reconstructBallot("someTitle", someChoices, new Page());

    assertEquals("someTitle", reconstructedBallot.getTitle());
    assertEquals("someChoice1", reconstructedBallot.getChoice("someChoice1").getDescription());
    assertEquals("someChoice2", reconstructedBallot.getChoice("someChoice2").getDescription());
    assertTrue(reconstructedBallot.getChoice("someChoice1").getHasVotedFor(SOME_USER1.getName()));
  }

  @Test
  public void test_createSurvey_noParameters_success() {
    final Survey returnedSurvey = classUnderTest.createSurvey("", new Page(), "");

    assertEquals(0, returnedSurvey.getBallots().size());
  }

  @Test
  public void test_createSurvey_oneParameter_success() {
    final String someBallotTitle1 = "someBallotTitle1";
    final Survey returnedSurvey = classUnderTest.createSurvey(someBallotTitle1, new Page(), null);

    assertEquals(someBallotTitle1, returnedSurvey.getBallot(someBallotTitle1).getTitle());
  }

  @Test
  public void test_createSurvey_twoParameters_success() {
    final String someBallotTitle1 = "someBallotTitle1";
    final String someBallotTitle2 = "someBallotTitle2";
    final String someBallotDescription1 = "someBallotDescription1";
    final Survey returnedSurvey = classUnderTest.createSurvey(someBallotTitle1 + " - " + someBallotDescription1 + "\r\n" + someBallotTitle2, new Page(), "");

    assertEquals(someBallotTitle1, returnedSurvey.getBallot(someBallotTitle1).getTitle());
    assertEquals(someBallotDescription1, returnedSurvey.getBallot(someBallotTitle1).getDescription());
    assertEquals(someBallotTitle2, returnedSurvey.getBallot(someBallotTitle2).getTitle());
  }

  @Test
  public void test_createSurvey_twoParametersWithCommenter_success() {
    final String someBallotTitle1 = "someBallotTitle1";
    final String someBallotTitle2 = "someBallotTitle2";

    final Page somePage = new Page();

    when(mockContentPropertyManager.getTextProperty(somePage, "survey." + someBallotTitle1 + ".commenters")).thenReturn(SOME_USER1.getName());
    when(mockContentPropertyManager.getTextProperty(somePage, "survey." + someBallotTitle1 + ".comment." + SOME_USER1.getName())).thenReturn("someComment");

    final Survey returnedSurvey = classUnderTest.createSurvey(someBallotTitle1 + "\r\n" + someBallotTitle2, somePage, "");

    assertEquals(someBallotTitle1, returnedSurvey.getBallot(someBallotTitle1).getTitle());
    assertEquals(someBallotTitle2, returnedSurvey.getBallot(someBallotTitle2).getTitle());
    assertEquals("someComment", returnedSurvey.getBallot(someBallotTitle1).getCommentForUser(SOME_USER1.getName()).getComment());
  }
}