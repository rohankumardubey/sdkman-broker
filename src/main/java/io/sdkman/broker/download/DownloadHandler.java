package io.sdkman.broker.download;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.sdkman.broker.audit.AuditEntry;
import io.sdkman.broker.audit.AuditRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Context;
import ratpack.handling.Handler;

import java.util.List;
import java.util.Optional;

@Singleton
public class DownloadHandler implements Handler {

    private final static Logger LOG = LoggerFactory.getLogger(DownloadHandler.class);

    private final static String COMMAND = "install";

    private VersionRepo versionRepo;
    private AuditRepo auditRepo;
    private DownloadResolver downloadResolver;

    @Inject
    public DownloadHandler(VersionRepo versionRepo, AuditRepo auditRepo, DownloadResolver downloadResolver) {
        this.versionRepo = versionRepo;
        this.auditRepo = auditRepo;
        this.downloadResolver = downloadResolver;
    }

    @Override
    public void handle(Context ctx) throws Exception {
        RequestDetails details = RequestDetails.of(ctx);
        LOG.info("Received download request for: " + details.getCandidate() + " " + details.getVersion());

        Optional<Platform> platform = Platform.of(details.getUname());
        if (!platform.isPresent()) ctx.clientError(400);
        else platform.ifPresent(p -> versionRepo
                .fetch(details.getCandidate(), details.getVersion())
                .then((List<Version> downloads) -> {
                    Optional<Version> resolved = downloadResolver.resolve(downloads, p.name());
                    if (!resolved.isPresent()) ctx.clientError(404);
                    resolved.ifPresent(v -> {
                        record(details, p.uname(), v.getPlatform());
                        ctx.redirect(302, v.getUrl());
                    });
                }));
    }

    private void record(RequestDetails details, String uname, String platform) {
        try {
            auditRepo.record(
                    AuditEntry.of(COMMAND, details.getCandidate(), details.getVersion(), details.getHost(),
                    details.getAgent(), uname, platform));
        } catch (Exception e) {
            LOG.error("Unable record audit entry: " + details + " - " + e.getMessage());
        }
    }
}
