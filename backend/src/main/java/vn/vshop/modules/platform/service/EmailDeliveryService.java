package vn.vshop.modules.platform.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.vshop.modules.identity.service.EmailPreferenceService;
import vn.vshop.modules.platform.gateway.EmailSender;
import vn.vshop.modules.platform.repository.EmailDeliveryRepository;

/**
 * Email copies of notifications. A delivery row is created next to each notification of a person
 * who turned email on; a worker sends them later, outside any database transaction. The address is
 * read again at send time, so turning email off stops mail still waiting. A failed send is retried
 * with growing delays and given up after five attempts; the notification itself is unaffected.
 */
@Service
public class EmailDeliveryService {
    private static final Logger LOG = LoggerFactory.getLogger(EmailDeliveryService.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final long[] BACKOFF_MINUTES = {1, 5, 30, 120};

    private final EmailDeliveryRepository deliveries;
    private final EmailPreferenceService preferences;
    private final EmailSender sender;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final String webBase;

    EmailDeliveryService(
            EmailDeliveryRepository deliveries,
            EmailPreferenceService preferences,
            EmailSender sender,
            Clock clock,
            PlatformTransactionManager transactions,
            @Value("${vshop.web.base-url:http://localhost:3100}") String webBase) {
        this.deliveries = deliveries;
        this.preferences = preferences;
        this.sender = sender;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
        this.webBase = webBase.replaceAll("/+$", "");
    }

    public boolean available() {
        return sender.configured();
    }

    /**
     * Queues an email for a notification when the mail server is set up and the person opted in.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(UUID notificationId, UUID accountId) {
        if (sender.configured() && preferences.emailFor(accountId).isPresent()) {
            deliveries.insert(notificationId, accountId);
        }
    }

    /** Sends what is due. Safe to run in several processes at once. Returns how many were sent. */
    public int sendDue(int limit) {
        if (!sender.configured()) {
            return 0;
        }
        List<EmailDeliveryRepository.Due> due = tx.execute(s -> deliveries.claimDue(limit));
        int sent = 0;
        for (EmailDeliveryRepository.Due mail : due) {
            Optional<String> to = tx.execute(s -> preferences.emailFor(mail.accountId()));
            if (to == null || to.isEmpty()) {
                tx.executeWithoutResult(s -> deliveries.markSkipped(mail.id(), "email turned off"));
                continue;
            }
            try {
                sender.send(compose(to.get(), mail));
                tx.executeWithoutResult(s -> deliveries.markSent(mail.id()));
                sent++;
            } catch (RuntimeException e) {
                LOG.warn(
                        "Email {} failed (attempt {}): {}",
                        mail.id(),
                        mail.attempts(),
                        e.toString());
                String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                if (mail.attempts() >= MAX_ATTEMPTS) {
                    tx.executeWithoutResult(s -> deliveries.markFailed(mail.id(), reason));
                } else {
                    long minutes =
                            BACKOFF_MINUTES[
                                    Math.min(mail.attempts() - 1, BACKOFF_MINUTES.length - 1)];
                    OffsetDateTime at = OffsetDateTime.now(clock).plusMinutes(minutes);
                    tx.executeWithoutResult(s -> deliveries.retryLater(mail.id(), reason, at));
                }
            }
        }
        return sent;
    }

    private EmailSender.Message compose(String to, EmailDeliveryRepository.Due mail) {
        String subject = oneLine("[VShop] " + mail.title());
        String url = mail.link() == null ? webBase : webBase + mail.link();
        String settings = webBase + "/notifications/settings";
        String text =
                mail.body()
                        + "\n\nXem chi tiết: "
                        + url
                        + "\n\n--\nBạn nhận email này vì đã bật thông báo email trên VShop. Tắt tại "
                        + settings;
        String html =
                "<p>"
                        + escape(mail.body())
                        + "</p><p><a href=\""
                        + escape(url)
                        + "\">Xem chi tiết</a></p><hr><p style=\"color:#777;font-size:12px\">"
                        + "Bạn nhận email này vì đã bật thông báo email trên VShop. "
                        + "<a href=\""
                        + escape(settings)
                        + "\">Tắt thông báo email</a></p>";
        return new EmailSender.Message(to, subject, text, html);
    }

    /** Escapes text for an HTML body, including attribute values. */
    private static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** Header values must be one line: a line break in a subject could inject extra headers. */
    private static String oneLine(String s) {
        return s.replaceAll("[\\r\\n]+", " ").trim();
    }
}
