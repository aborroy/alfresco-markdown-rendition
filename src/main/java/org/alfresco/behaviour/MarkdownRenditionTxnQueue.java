package org.alfresco.behaviour;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.alfresco.repo.transaction.*;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.transaction.TransactionService;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import java.util.*;

public class MarkdownRenditionTxnQueue implements TransactionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarkdownRenditionTxnQueue.class);
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
        Set<Job> jobSet = TransactionalResourceHelper.getSet(KEY_JOBS);
        final List<Job> jobs = new ArrayList<>(jobSet);
        jobSet.clear();

        // Submit jobs asynchronously. CallerRunsPolicy provides automatic backpressure
        // by executing rejected tasks in the calling thread when the pool is saturated.
        for (Job job : jobs) {
            taskExecutor.execute(() -> runJob(job));
        }
    }

    private void runJob(Job job) {
        AuthenticationUtil.runAsSystem(() -> {
            try {
                transactionService.getRetryingTransactionHelper()
                        .doInTransaction(() -> {
                            markdownCreator.ensureMarkdownRendition(job.original, job.pdfSource);
                            return null;
                        }, false, true);
            } catch (Exception e) {
                LOGGER.error("Failed to create markdown rendition for {} from {}",
                        job.original, job.pdfSource, e);
            }
            return null;
        });
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