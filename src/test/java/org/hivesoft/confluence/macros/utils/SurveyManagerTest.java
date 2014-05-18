package org.hivesoft.confluence.macros.utils;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.core.ContentEntityObject;
import com.atlassian.confluence.core.ContentPropertyManager;
import com.atlassian.confluence.pages.Comment;
import com.atlassian.confluence.pages.Page;
import com.atlassian.renderer.v2.macro.MacroException;
import com.atlassian.user.impl.DefaultUser;
import org.hivesoft.confluence.macros.survey.model.Survey;
import org.hivesoft.confluence.macros.vote.VoteConfig;
import org.hivesoft.confluence.macros.vote.VoteMacro;
import org.hivesoft.confluence.macros.vote.model.Ballot;
import org.hivesoft.confluence.macros.vote.model.Choice;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class SurveyManagerTest {
  private final static DefaultUser SOME_USER1 = new DefaultUser("someUser1", "someUser1 FullName", "some1@testmail.de");

  ContentPropertyManager mockContentPropertyManager = mock(ContentPropertyManager.class);
  PermissionEvaluator mockPermissionEvaluator = mock(PermissionEvaluator.class);

  HttpServletRequest mockRequest = mock(HttpServletRequest.class);

  SurveyManager classUnderTest;

  @Before
  public void setup() {
    when(mockPermissionEvaluator.getRemoteUsername()).thenReturn(SurveyUtilsTest.SOME_USER_NAME);
    when(mockRequest.getParameter(VoteMacro.REQUEST_PARAMETER_BALLOT)).thenReturn(SurveyUtilsTest.SOME_BALLOT_TITLE);
    when(mockRequest.getParameter(VoteMacro.REQUEST_PARAMETER_CHOICE)).thenReturn(SurveyUtilsTest.SOME_CHOICE_DESCRIPTION);

    classUnderTest = new SurveyManager(mockContentPropertyManager, mockPermissionEvaluator);
  }

  @Test
  public void test_reconstructBallot_noChoices_success() throws MacroException {
    final Ballot reconstructedBallot = classUnderTest.reconstructBallotFromPlainTextMacroBody(SurveyUtilsTest.createDefaultParametersWithTitle("someTitle"), "", new Page());

    assertEquals("someTitle", reconstructedBallot.getTitle());
    Assert.assertThat(reconstructedBallot.getChoices().size(), is(0));
  }

  @Test
  public void test_reconstructBallot_someChoices_noVoter_success() throws MacroException {
    String someChoices = "someChoice1\r\nsomeChoice2";

    final Ballot reconstructedBallot = classUnderTest.reconstructBallotFromPlainTextMacroBody(SurveyUtilsTest.createDefaultParametersWithTitle("someTitle"), someChoices, new Page());

    assertEquals("someTitle", reconstructedBallot.getTitle());
    assertEquals("someChoice1", reconstructedBallot.getChoice("someChoice1").getDescription());
    assertEquals("someChoice2", reconstructedBallot.getChoice("someChoice2").getDescription());
    assertFalse(reconstructedBallot.getChoice("someChoice1").getHasVotedFor(SOME_USER1.getName()));
  }

  @Test
  public void test_reconstructBallot_someChoices_withVoter_success() throws MacroException {
    String someChoices = "someChoice1\r\nsomeChoice2";

    when(mockContentPropertyManager.getTextProperty(any(ContentEntityObject.class), anyString())).thenReturn(SOME_USER1.getName());
    final Ballot reconstructedBallot = classUnderTest.reconstructBallotFromPlainTextMacroBody(SurveyUtilsTest.createDefaultParametersWithTitle("someTitle"), someChoices, new Page());

    assertEquals("someTitle", reconstructedBallot.getTitle());
    assertEquals("someChoice1", reconstructedBallot.getChoice("someChoice1").getDescription());
    assertEquals("someChoice2", reconstructedBallot.getChoice("someChoice2").getDescription());
    assertTrue(reconstructedBallot.getChoice("someChoice1").getHasVotedFor(SOME_USER1.getName()));
  }

  @Test
  public void test_reconstructSurvey_noParametersWithTitle_success() {
    final Survey returnedSurvey = classUnderTest.reconstructSurveyFromPlainTextMacroBody("", new Page(), SurveyUtilsTest.createDefaultParametersWithTitle("someTitle"));

    assertEquals(0, returnedSurvey.getBallots().size());
    assertThat(returnedSurvey.getTitle(), is(equalTo("someTitle")));
  }

  @Test
  public void test_reconstructSurvey_oneParameterDefaultChoices_success() {
    final String someBallotTitle1 = "someBallotTitle1";
    final Survey returnedSurvey = classUnderTest.reconstructSurveyFromPlainTextMacroBody(someBallotTitle1, new Page(), new HashMap<String, String>());

    assertEquals(someBallotTitle1, returnedSurvey.getBallot(someBallotTitle1).getTitle());
    assertThat(returnedSurvey.getBallot(someBallotTitle1).getChoices().size(), is(5));
  }

  @Test
  public void test_reconstructSurvey_oneParameterCustomChoices_success() {
    final String someBallotTitle1 = "someBallotTitle1";
    final Survey returnedSurvey = classUnderTest.reconstructSurveyFromPlainTextMacroBody(someBallotTitle1 + " - subTitle - choice1 - choice2", new Page(), new HashMap<String, String>());

    assertEquals(someBallotTitle1, returnedSurvey.getBallot(someBallotTitle1).getTitle());
    assertThat(returnedSurvey.getBallot(someBallotTitle1).getChoices().size(), is(2));
    assertThat(returnedSurvey.getBallot(someBallotTitle1).getChoices().iterator().next().getDescription(), is(equalTo("choice1")));
  }

  @Test
  public void test_reconstructSurvey_twoParameters_success() {
    final String someBallotTitle1 = "someBallotTitle1";
    final String someBallotTitle2 = "someBallotTitle2";
    final String someBallotDescription1 = "someBallotDescription1";

    final Survey returnedSurvey = classUnderTest.reconstructSurveyFromPlainTextMacroBody(someBallotTitle1 + " - " + someBallotDescription1 + "\r\n" + someBallotTitle2, new Page(), SurveyUtilsTest.createDefaultParametersWithTitle("someTitle"));

    assertThat(returnedSurvey.getBallot(someBallotTitle1).getTitle(), is(equalTo(someBallotTitle1)));
    assertThat(returnedSurvey.getBallot(someBallotTitle1).getDescription(), is(equalTo(someBallotDescription1)));
    assertThat(returnedSurvey.getBallot(someBallotTitle2).getTitle(), is(equalTo(someBallotTitle2)));
    assertThat(returnedSurvey.getBallot(someBallotTitle2).getChoices().size(), is(5));
  }

  @Test
  public void test_reconstructSurvey_twoParametersWithCommenter_success() {
    final String someBallotTitle1 = "someBallotTitle1";
    final String someBallotTitle2 = "someBallotTitle2";

    final Page somePage = new Page();

    final String userName1 = SOME_USER1.getName();
    final String userName2 = "someUser2";
    final String commentUsers = userName1 + SurveyManager.COMMENTERS_SEPARATOR + userName2;
    final String commentForUser1 = "someComment";
    final String commentForUser2 = "another Comment";
    when(mockContentPropertyManager.getTextProperty(somePage, "survey." + someBallotTitle1 + ".commenters")).thenReturn(commentUsers);
    when(mockContentPropertyManager.getTextProperty(somePage, "survey." + someBallotTitle1 + ".comment." + userName1)).thenReturn(commentForUser1);
    when(mockContentPropertyManager.getTextProperty(somePage, "survey." + someBallotTitle1 + ".comment." + userName2)).thenReturn(commentForUser2);

    final Survey returnedSurvey = classUnderTest.reconstructSurveyFromPlainTextMacroBody(someBallotTitle1 + "\r\n" + someBallotTitle2, somePage, SurveyUtilsTest.createDefaultParametersWithTitle("someTitle"));

    final Ballot returnedBallot1 = returnedSurvey.getBallot(someBallotTitle1);

    assertThat(returnedBallot1.getTitle(), is(someBallotTitle1));
    assertThat(returnedSurvey.getBallot(someBallotTitle2).getTitle(), is(someBallotTitle2));
    assertThat(returnedBallot1.getCommentForUser(userName1).getComment(), is(commentForUser1));
    assertThat(returnedBallot1.getCommentForUser(userName2).getComment(), is(commentForUser2));
    assertThat(returnedBallot1.getComments(), hasSize(2));
  }

  @Test
  public void test_reconstructSurvey_twoParametersWithVotes_success() {
    final String someBallotTitle1 = "someBallotTitle1";
    final String someBallotTitle2 = "someBallotTitle2";

    final Page somePage = new Page();

    when(mockContentPropertyManager.getTextProperty(somePage, VoteMacro.VOTE_PREFIX + someBallotTitle1 + ".5-Outstanding")).thenReturn(SOME_USER1.getName());

    final Survey returnedSurvey = classUnderTest.reconstructSurveyFromPlainTextMacroBody(someBallotTitle1 + "\r\n" + someBallotTitle2, somePage, SurveyUtilsTest.createDefaultParametersWithTitle("someTitle"));

    assertEquals(someBallotTitle1, returnedSurvey.getBallot(someBallotTitle1).getTitle());
    assertEquals(someBallotTitle2, returnedSurvey.getBallot(someBallotTitle2).getTitle());
    assertThat(returnedSurvey.getBallot(someBallotTitle1).getChoice("5-Outstanding").getHasVotedFor(SOME_USER1.getName()), is(true));
  }

  @Test
  public void test_recordVote_noUser_success() {
    Ballot ballot = SurveyUtilsTest.createDefaultBallot(SurveyUtilsTest.SOME_BALLOT_TITLE);
    when(mockPermissionEvaluator.getRemoteUsername()).thenReturn("");

    classUnderTest.recordVote(ballot, mockRequest, new Page());

    verify(mockContentPropertyManager, times(0)).setTextProperty(any(ContentEntityObject.class), anyString(), anyString());
  }

  @Test
  public void test_recordVote_freshVote_success() {
    Choice choiceToVoteOn = SurveyUtilsTest.createdDefaultChoice();
    Ballot ballot = SurveyUtilsTest.createDefaultBallotWithChoices(SurveyUtilsTest.SOME_BALLOT_TITLE, Arrays.asList(choiceToVoteOn));

    when(mockPermissionEvaluator.getCanVote(anyString(), any(Ballot.class))).thenReturn(true);
    when(mockRequest.getParameter(VoteMacro.REQUEST_PARAMETER_VOTE_ACTION)).thenReturn("vote");

    classUnderTest.recordVote(ballot, mockRequest, new Page());

    verify(mockContentPropertyManager, times(1)).setTextProperty(any(ContentEntityObject.class), anyString(), anyString());
  }

  @Test
  public void test_recordVote_alreadyVotedOnDifferentChangeAbleVotesTrue_success() {
    Choice choiceAlreadyVotedOn = new Choice("already Voted on");
    Choice choiceToVoteOn = new Choice(SurveyUtilsTest.SOME_CHOICE_DESCRIPTION);

    Map<String, String> parameters = new HashMap<String, String>();
    parameters.put(VoteConfig.KEY_TITLE, SurveyUtilsTest.SOME_BALLOT_TITLE);
    parameters.put(VoteConfig.KEY_CHANGEABLE_VOTES, "true");

    Ballot ballot = SurveyUtilsTest.createBallotWithParametersAndChoices(parameters, Arrays.asList(choiceAlreadyVotedOn, choiceToVoteOn));

    choiceAlreadyVotedOn.voteFor(SurveyUtilsTest.SOME_USER_NAME);

    when(mockPermissionEvaluator.getCanVote(anyString(), any(Ballot.class))).thenReturn(true);
    when(mockRequest.getParameter(VoteMacro.REQUEST_PARAMETER_VOTE_ACTION)).thenReturn("vote");

    classUnderTest.recordVote(ballot, mockRequest, new Page());

    verify(mockContentPropertyManager, times(2)).setTextProperty(any(ContentEntityObject.class), anyString(), anyString());
  }

  @Test
  public void test_recordVote_alreadyVotedOnUnvoteChangeAbleVotesTrue_success() {
    Choice choiceAlreadyVotedOn = new Choice("already Voted on");

    Map<String, String> parameters = new HashMap<String, String>();
    parameters.put(VoteConfig.KEY_TITLE, SurveyUtilsTest.SOME_BALLOT_TITLE);
    parameters.put(VoteConfig.KEY_CHANGEABLE_VOTES, "true");

    Ballot ballot = SurveyUtilsTest.createBallotWithParametersAndChoices(parameters, Arrays.asList(choiceAlreadyVotedOn));

    choiceAlreadyVotedOn.voteFor(SurveyUtilsTest.SOME_USER_NAME);

    when(mockPermissionEvaluator.getCanVote(anyString(), any(Ballot.class))).thenReturn(true);
    when(mockRequest.getParameter(VoteMacro.REQUEST_PARAMETER_VOTE_ACTION)).thenReturn("unvote");

    classUnderTest.recordVote(ballot, mockRequest, new Page());

    verify(mockContentPropertyManager, times(1)).setTextProperty(any(ContentEntityObject.class), anyString(), anyString());
  }

  @Test
  public void test_recordVote_alreadyVotedOnDifferentChangeAbleVotesFalse_success() {
    Ballot ballot = SurveyUtilsTest.createDefaultBallot(SurveyUtilsTest.SOME_BALLOT_TITLE);

    ballot.getChoices().iterator().next().voteFor(SurveyUtilsTest.SOME_USER_NAME);

    classUnderTest.recordVote(ballot, mockRequest, new Page());

    verify(mockContentPropertyManager, times(0)).setTextProperty(any(ContentEntityObject.class), anyString(), anyString());
  }

  @Test
  public void test_getPageEntityFromConversionContext_startingWithComment() {
    ConversionContext mockConversionContext = mock(ConversionContext.class);

    Page page = new Page();
    page.setId(123L);
    final Comment comment = new Comment();
    comment.setOwner(page);
    when(mockConversionContext.getEntity()).thenReturn(comment);

    final ContentEntityObject pageEntity = classUnderTest.getPageEntityFromConversionContext(mockConversionContext);

    assertThat(pageEntity, is(instanceOf(Page.class)));
  }

  @Test
  public void test_getPageEntityFromConversionContext_startingWithPage() {
    ConversionContext mockConversionContext = mock(ConversionContext.class);

    Page page = new Page();
    page.setId(123L);
    when(mockConversionContext.getEntity()).thenReturn(page);

    final ContentEntityObject pageEntity = classUnderTest.getPageEntityFromConversionContext(mockConversionContext);

    assertThat(pageEntity, is(instanceOf(Page.class)));
  }
}
