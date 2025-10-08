package org.alfresco.behaviour;

import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.namespace.QName;

import static org.alfresco.model.RenditionModel.ASSOC_RENDITION;

public class PdfRenditionAssociationBehaviour implements NodeServicePolicies.OnCreateChildAssociationPolicy {

    private static final String RN_URI = "http://www.alfresco.org/model/rendition/1.0";
    private static final String PDF = "pdf";

    private PolicyComponent policyComponent;
    private MarkdownRenditionTxnQueue markdownQueue;

    public void init() {
        policyComponent.bindAssociationBehaviour(
                NodeServicePolicies.OnCreateChildAssociationPolicy.QNAME,
                this,
                new JavaBehaviour(this, "onCreateChildAssociation", Behaviour.NotificationFrequency.TRANSACTION_COMMIT)
        );
    }

    @Override
    public void onCreateChildAssociation(ChildAssociationRef childAssocRef, boolean isNewNode) {
        QName assocType = childAssocRef.getTypeQName();
        QName assocQName = childAssocRef.getQName();

        if (RN_URI.equals(assocType.getNamespaceURI()) && PDF.equals(assocQName.getLocalName())) {
            // parent = original; child = newly created PDF rendition
            markdownQueue.queue(childAssocRef.getParentRef(), childAssocRef.getChildRef());
        }
    }

    // --- Spring
    public void setPolicyComponent(PolicyComponent policyComponent) { this.policyComponent = policyComponent; }
    public void setMarkdownQueue(MarkdownRenditionTxnQueue q) { this.markdownQueue = q; }
}
