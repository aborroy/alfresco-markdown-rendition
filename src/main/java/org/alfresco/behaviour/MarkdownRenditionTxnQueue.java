package org.alfresco.behaviour;

import org.alfresco.repo.transaction.*;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.transaction.TransactionService;
import org.springframework.core.task.TaskExecutor;

import java.util.*;

public class MarkdownRenditionTxnQueue implements TransactionListener {

    private static final String KEY_JOBS = MarkdownRenditionTxnQueue.class.getName() + ".jobs";

    private TransactionService transactionService;
    private TaskExecutor taskExecutor;
    private MarkdownRenditionCreator markdownCreator;

    public void queue(NodeRef original, NodeRef pdfSource) {
        AlfrescoTransactionSupport.bindListener(this);
        TransactionalResourceHelper.getSet(KEY_JOBS).add(new Job(original, pdfSource));
    }

    @Override
    public void afterCommit() {
        final List<Job> jobs = new ArrayList<>(TransactionalResourceHelper.getSet(KEY_JOBS));

        taskExecutor.execute(() -> jobs.forEach(job ->
                AuthenticationUtil.runAsSystem(() -> {
                    transactionService.getRetryingTransactionHelper()
                            .doInTransaction(() -> {
                                markdownCreator.ensureMarkdownRendition(job.original, job.pdfSource);
                                return null;
                            }, false, true);
                    return null;
                })
        ));
    }

    @Override
    public void beforeCommit(boolean readOnly) { }

    @Override
    public void beforeCompletion() { }

    @Override
    public void afterRollback() { }

    @Override
    public void flush() { }

    // Value object for deduplication in Set
    private record Job(NodeRef original, NodeRef pdfSource) { }

    // Spring setters
    public void setTransactionService(TransactionService ts) { this.transactionService = ts; }
    public void setTaskExecutor(TaskExecutor te) { this.taskExecutor = te; }
    public void setMarkdownCreator(MarkdownRenditionCreator mc) { this.markdownCreator = mc; }
}