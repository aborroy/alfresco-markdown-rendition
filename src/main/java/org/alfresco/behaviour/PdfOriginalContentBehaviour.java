package org.alfresco.behaviour;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.ContentServicePolicies;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.NodeRef;

public class PdfOriginalContentBehaviour
        implements ContentServicePolicies.OnContentUpdatePolicy, NodeServicePolicies.OnCreateNodePolicy {

    private static final String MIMETYPE_PDF = "application/pdf";

    private PolicyComponent policyComponent;
    private ServiceRegistry services;
    private MarkdownRenditionTxnQueue markdownQueue;

    public void init() {
        // On write of content
        policyComponent.bindClassBehaviour(
                ContentServicePolicies.OnContentUpdatePolicy.QNAME,
                ContentModel.TYPE_CONTENT,
                new JavaBehaviour(this, "onContentUpdate", Behaviour.NotificationFrequency.TRANSACTION_COMMIT)
        );
        // Safety net for create flows
        policyComponent.bindClassBehaviour(
                NodeServicePolicies.OnCreateNodePolicy.QNAME,
                ContentModel.TYPE_CONTENT,
                new JavaBehaviour(this, "onCreateNode", Behaviour.NotificationFrequency.TRANSACTION_COMMIT)
        );
    }

    @Override
    public void onContentUpdate(NodeRef nodeRef, boolean newContent) {
        maybeCreateFromSelfIfPdf(nodeRef);
    }

    @Override
    public void onCreateNode(org.alfresco.service.cmr.repository.ChildAssociationRef parentAssocRef) {
        maybeCreateFromSelfIfPdf(parentAssocRef.getChildRef());
    }

    private void maybeCreateFromSelfIfPdf(NodeRef nodeRef) {
        var nodeService = services.getNodeService();
        if (!nodeService.exists(nodeRef)) return;
        if (!ContentModel.TYPE_CONTENT.equals(nodeService.getType(nodeRef))) return;

        ContentReader r = services.getContentService().getReader(nodeRef, ContentModel.PROP_CONTENT);
        if (r == null || !r.exists()) return;
        if (!MIMETYPE_PDF.equalsIgnoreCase(r.getMimetype())) return;

        // parent = original; source = original (already a PDF)
        markdownQueue.queue(nodeRef, nodeRef);
    }

    // --- Spring
    public void setPolicyComponent(PolicyComponent policyComponent) { this.policyComponent = policyComponent; }
    public void setServiceRegistry(ServiceRegistry services) { this.services = services; }
    public void setMarkdownQueue(MarkdownRenditionTxnQueue q) { this.markdownQueue = q; }
}
