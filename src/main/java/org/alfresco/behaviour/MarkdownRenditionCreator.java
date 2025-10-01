package org.alfresco.behaviour;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.rendition2.SynchronousTransformClient;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.namespace.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.alfresco.service.namespace.NamespaceService.CONTENT_MODEL_1_0_URI;

public class MarkdownRenditionCreator {

    private static final Logger logger = LoggerFactory.getLogger(MarkdownRenditionCreator.class);

    private static final String RN_URI = "http://www.alfresco.org/model/rendition/1.0";
    private static final QName ASSOC_RENDITION = QName.createQName(RN_URI, "rendition");
    private static final QName QNAME_MARKDOWN = QName.createQName(CONTENT_MODEL_1_0_URI, "markdown");
    private static final QName ASPECT_RENDITION = QName.createQName(RN_URI, "rendition");
    private static final String MIMETYPE_MD = "text/markdown";

    private ServiceRegistry services;

    /**
     * Create (if missing) rn:rendition/cm:markdown for parentOriginal using pdfSource as input.
     */
    public void ensureMarkdownRendition(NodeRef parentOriginal, NodeRef pdfSource) {
        NodeService nodeService = services.getNodeService();
        ContentService contentService = services.getContentService();

        if (!nodeService.exists(parentOriginal) || !nodeService.exists(pdfSource)) {
            return;
        }

        // Idempotency: skip if markdown already present
        if (markdownExists(nodeService, parentOriginal)) {
            logger.debug("Markdown rendition already exists for {}", parentOriginal);
            return;
        }

        // Read and transform PDF to Markdown
        ContentReader pdfReader = contentService.getReader(pdfSource, ContentModel.PROP_CONTENT);
        if (pdfReader == null || !pdfReader.exists()) {
            logger.warn("PDF content not found: {}", pdfSource);
            return;
        }

        ContentReader mdReader = transformToMarkdown(pdfReader, contentService, pdfSource);
        if (mdReader == null || !mdReader.exists()) {
            logger.warn("Markdown transform returned empty content for {}", parentOriginal);
            return;
        }

        // Persist as rendition child
        createMarkdownRendition(parentOriginal, mdReader, nodeService, contentService);
        logger.info("Created rn:rendition/cm:markdown for {}", parentOriginal);
    }

    private boolean markdownExists(NodeService nodeService, NodeRef parent) {
        return nodeService
                .getChildAssocs(parent, ASSOC_RENDITION, QNAME_MARKDOWN)
                .stream()
                .findFirst()
                .isPresent();
    }

    private ContentReader transformToMarkdown(ContentReader pdfReader,
                                              ContentService contentService,
                                              NodeRef pdfSource) {
        SynchronousTransformClient transformClient = services.getSynchronousTransformClient();

        ContentWriter tempWriter = contentService.getTempWriter();
        tempWriter.setMimetype(MIMETYPE_MD);
        tempWriter.setEncoding(pdfReader.getEncoding());

        transformClient.transform(pdfReader, tempWriter,
                Map.of("image", "described"), null, pdfSource);

        return tempWriter.getReader();
    }

    private void createMarkdownRendition(NodeRef parent, ContentReader mdReader,
                                         NodeService nodeService, ContentService contentService) {
        NodeRef markdownNode = nodeService.createNode(
                parent, ASSOC_RENDITION, QNAME_MARKDOWN, ContentModel.TYPE_CONTENT
        ).getChildRef();

        if (!nodeService.hasAspect(markdownNode, ASPECT_RENDITION)) {
            nodeService.addAspect(markdownNode, ASPECT_RENDITION, null);
        }

        ContentWriter writer = contentService.getWriter(markdownNode, ContentModel.PROP_CONTENT, true);
        writer.setMimetype(MIMETYPE_MD);
        writer.setEncoding(mdReader.getEncoding());
        writer.putContent(mdReader);
    }

    public void setServiceRegistry(ServiceRegistry services) {
        this.services = services;
    }
}