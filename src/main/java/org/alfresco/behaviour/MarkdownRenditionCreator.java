package org.alfresco.behaviour;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.rendition2.SynchronousTransformClient;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.namespace.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.alfresco.service.namespace.NamespaceService.CONTENT_MODEL_1_0_URI;

public class MarkdownRenditionCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarkdownRenditionCreator.class);

    private static final String RN_URI = "http://www.alfresco.org/model/rendition/1.0";
    private static final QName ASSOC_RENDITION = QName.createQName(RN_URI, "rendition");
    private static final QName QNAME_MARKDOWN = QName.createQName(CONTENT_MODEL_1_0_URI, "markdown");
    private static final QName ASPECT_RENDITION = QName.createQName(RN_URI, "rendition");
    private static final String MIMETYPE_MD = "text/markdown";

    private ServiceRegistry services;
    private String images;
    private String language;

    /**
     * Create rn:rendition/cm:markdown for parentOriginal using pdfSource as input.
     */
    public void ensureMarkdownRendition(NodeRef parentOriginal, NodeRef pdfSource) {
        NodeService nodeService = services.getNodeService();
        ContentService contentService = services.getContentService();

        if (!nodeService.exists(parentOriginal) || !nodeService.exists(pdfSource)) {
            return;
        }

        // Read and transform PDF to Markdown
        ContentReader pdfReader = contentService.getReader(pdfSource, ContentModel.PROP_CONTENT);
        if (pdfReader == null || !pdfReader.exists()) {
            LOGGER.warn("PDF content not found: {}", pdfSource);
            return;
        }

        ContentReader mdReader = transformToMarkdown(pdfReader, contentService, pdfSource);
        if (mdReader == null || !mdReader.exists()) {
            LOGGER.warn("Markdown transform returned empty content for {}", parentOriginal);
            return;
        }

        // Update existing or create new
        updateOrCreateMarkdownRendition(parentOriginal, mdReader, nodeService, contentService);
        LOGGER.info("Ensured rn:rendition/cm:markdown for {}", parentOriginal);
    }

    private void updateOrCreateMarkdownRendition(NodeRef parent, ContentReader mdReader,
                                                 NodeService nodeService, ContentService contentService) {
        // Try to find existing markdown rendition
        List<ChildAssociationRef> existingAssocs = nodeService
                .getChildAssocs(parent, ASSOC_RENDITION, QNAME_MARKDOWN);

        NodeRef markdownNode;

        if (!existingAssocs.isEmpty()) {
            // Update existing rendition
            markdownNode = existingAssocs.get(0).getChildRef();

            // If somehow there are duplicates, remove them
            if (existingAssocs.size() > 1) {
                LOGGER.warn("Found {} duplicate markdown renditions for {}, cleaning up",
                        existingAssocs.size() - 1, parent);
                for (int i = 1; i < existingAssocs.size(); i++) {
                    nodeService.deleteNode(existingAssocs.get(i).getChildRef());
                }
            }

            LOGGER.debug("Updating existing markdown rendition: {}", markdownNode);
        } else {
            // Create new rendition
            try {
                markdownNode = nodeService.createNode(
                        parent, ASSOC_RENDITION, QNAME_MARKDOWN, ContentModel.TYPE_CONTENT
                ).getChildRef();

                if (!nodeService.hasAspect(markdownNode, ASPECT_RENDITION)) {
                    nodeService.addAspect(markdownNode, ASPECT_RENDITION, null);
                }
                LOGGER.debug("Created new markdown rendition: {}", markdownNode);
            } catch (DuplicateChildNodeNameException e) {
                // Race condition: another thread created it
                LOGGER.debug("Concurrent creation detected, fetching existing rendition");
                existingAssocs = nodeService.getChildAssocs(parent, ASSOC_RENDITION, QNAME_MARKDOWN);
                if (existingAssocs.isEmpty()) {
                    throw new RuntimeException("Failed to create or find markdown rendition", e);
                }
                markdownNode = existingAssocs.get(0).getChildRef();
            }
        }

        // Write/update content
        if (!nodeService.exists(markdownNode)) {
            LOGGER.warn("Markdown node disappeared during processing: {}", markdownNode);
            return;
        }

        ContentWriter writer = contentService.getWriter(markdownNode, ContentModel.PROP_CONTENT, true);
        writer.setMimetype(MIMETYPE_MD);
        writer.setEncoding(mdReader.getEncoding());
        writer.putContent(mdReader);
    }

    private ContentReader transformToMarkdown(ContentReader pdfReader,
                                              ContentService contentService,
                                              NodeRef pdfSource) {
        SynchronousTransformClient transformClient = services.getSynchronousTransformClient();
        ContentWriter tempWriter = contentService.getTempWriter();
        tempWriter.setMimetype(MIMETYPE_MD);
        tempWriter.setEncoding(pdfReader.getEncoding());

        try {
            transformClient.transform(pdfReader, tempWriter,
                    Map.of("image", images, "language", language), null, pdfSource);

            ContentReader reader = tempWriter.getReader();
            if (reader.getSize() == 0) {
                LOGGER.warn("Transform produced empty content for {}", pdfSource);
                return null;
            }
            return reader;
        } catch (Exception e) {
            LOGGER.error("Transform failed for {}", pdfSource, e);
            return null;
        }
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
    public void setImages(String images) { this.images = images; }
    public void setLanguage(String language) { this.language = language; }

}