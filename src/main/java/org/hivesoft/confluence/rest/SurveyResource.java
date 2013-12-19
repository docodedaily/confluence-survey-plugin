package org.hivesoft.confluence.rest;

import au.com.bytecode.opencsv.CSVWriter;
import com.atlassian.confluence.content.render.xhtml.DefaultConversionContext;
import com.atlassian.confluence.content.render.xhtml.XhtmlException;
import com.atlassian.confluence.core.ContentPropertyManager;
import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.xhtml.api.MacroDefinition;
import com.atlassian.confluence.xhtml.api.MacroDefinitionHandler;
import com.atlassian.confluence.xhtml.api.XhtmlContent;
import com.atlassian.extras.common.log.Logger;
import com.atlassian.renderer.v2.macro.MacroException;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import org.apache.commons.lang3.StringUtils;
import org.hivesoft.confluence.macros.survey.SurveyMacro;
import org.hivesoft.confluence.macros.survey.model.Survey;
import org.hivesoft.confluence.macros.utils.SurveyUtils;
import org.hivesoft.confluence.macros.vote.model.Ballot;
import org.hivesoft.confluence.macros.vote.model.Choice;
import org.hivesoft.confluence.macros.vote.model.Comment;
import org.hivesoft.confluence.rest.callbacks.TransactionCallbackAddAttachment;
import org.hivesoft.confluence.rest.representations.CSVExportRepresentation;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.*;

@Path("/pages/{pageId}/surveys")
public class SurveyResource {
  private static final Logger.Log LOG = Logger.getInstance(SurveyResource.class);

  protected final TransactionTemplate transactionTemplate;
  protected final PageManager pageManager;
  protected final ContentPropertyManager contentPropertyManager;
  protected final XhtmlContent xhtmlContent;
  protected final I18nResolver i18nResolver;

  public SurveyResource(TransactionTemplate transactionTemplate, PageManager pageManager, ContentPropertyManager contentPropertyManager, XhtmlContent xhtmlContent, I18nResolver i18nResolver) {
    this.transactionTemplate = transactionTemplate;
    this.pageManager = pageManager;
    this.contentPropertyManager = contentPropertyManager;
    this.xhtmlContent = xhtmlContent;
    this.i18nResolver = i18nResolver;
  }

  @GET
  @Path("/{surveyTitle}/export")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getCSVExportForSurvey(@PathParam("pageId") long pageId, @PathParam("surveyTitle") String inSurveyTitle) throws UnsupportedEncodingException {
    final String surveyTitle = URLDecoder.decode(inSurveyTitle, "UTF-8");
    final Page page = pageManager.getPage(pageId);

    if (page == null) {
      return Response.status(Response.Status.NOT_FOUND.getStatusCode()).entity("Specified page with id: " + pageId + " was not found").build();
    }
    LOG.info("Found page with id=" + pageId + " and title=" + page.getTitle());

    //page.addAttachment(new Attachment());

    final List<Survey> surveys = new ArrayList<Survey>();
    try {
      xhtmlContent.handleMacroDefinitions(page.getBodyAsString(), new DefaultConversionContext(page.getContentEntityObject().toPageContext()), new MacroDefinitionHandler() {
        @Override
        public void handle(MacroDefinition macroDefinition) {
          if (SurveyMacro.SURVEY_MACRO.equals(macroDefinition.getName())) {
            final Map<String, String> parameters = macroDefinition.getParameters();
            String currentTitle = null;
            try {
              currentTitle = SurveyUtils.getTitleInMacroParameters(parameters);
            } catch (MacroException e) {
              e.printStackTrace();
            }
            LOG.info("surveyTitle for export=" + surveyTitle + ", currentTitle to check is=" + currentTitle);
            if (surveyTitle.equalsIgnoreCase(currentTitle)) {
              LOG.info("Try to reconstruct Survey...");
              surveys.add(SurveyUtils.createSurvey(macroDefinition.getBodyText(), page.getContentEntityObject(), macroDefinition.getParameters().get(SurveyMacro.KEY_CHOICES), contentPropertyManager));
            }
          }
        }
      });
    } catch (XhtmlException e) {
      e.printStackTrace();
      return Response.status(Response.Status.BAD_REQUEST.getStatusCode()).entity("There was a problem reconstructing the given survey with title: " + surveyTitle).build();
    }

    if (!surveys.isEmpty()) {
      Calendar currentDate = new GregorianCalendar();
      final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd'T'hhmmss");
      final String fileName = surveyTitle + "-summary-" + simpleDateFormat.format(currentDate.getTime()) + ".csv";

      final Survey survey = surveys.iterator().next();

      final StringWriter csvStringWriter = new StringWriter();
      CSVWriter writer = new CSVWriter(csvStringWriter, ';');

      writer.writeNext(new String[]{
              i18nResolver.getText("surveyplugin.survey.summary.header.question"),
              i18nResolver.getText("surveyplugin.vote.choices"),
              i18nResolver.getText("surveyplugin.vote.voters"),
              i18nResolver.getText("surveyplugin.export.comments")
      });

      List<String> comments = new ArrayList<String>();
      for (Ballot ballot : survey.getBallots()) {
        for (Choice choice : ballot.getChoices()) {
          comments.clear();
          for (String voter : choice.getVoters()) {
            Comment comment = ballot.getCommentForUser(voter);
            if (comment != null) {
              comments.add(comment.getComment());
            }
          }
          String[] line = new String[]{ballot.getTitle(), choice.getDescription(), StringUtils.join(choice.getVoters().toArray(), ","), StringUtils.join(comments.toArray(), ",")};
          writer.writeNext(line);
        }
      }

      // feed in your array (or convert your data to an array)
      //String[] entries = "first#second#third".split("#");
      //writer.writeNext(entries);
      try {
        writer.close();
      } catch (IOException e) {
        e.printStackTrace();
      }

      final byte[] csvBytes = csvStringWriter.toString().getBytes("UTF-8");

      LOG.info("attachment does not already exist, carry on");
      final Attachment addedAttachment = transactionTemplate.execute(new TransactionCallbackAddAttachment(pageManager, page, fileName, csvBytes));

      if (addedAttachment == null) {
        throw new IllegalArgumentException("Could not save Attachment");
      }
      final String attachmentUrlPath = addedAttachment.getDownloadPath();
      LOG.info("returning path to attachment: " + attachmentUrlPath);

      final CSVExportRepresentation csvExportRepresentation = new CSVExportRepresentation(attachmentUrlPath);
      return Response.ok(csvExportRepresentation).build();
    }
    return Response.status(Response.Status.BAD_REQUEST).entity("Could not find the specified survey macro on the specified page!").build();
  }
}
