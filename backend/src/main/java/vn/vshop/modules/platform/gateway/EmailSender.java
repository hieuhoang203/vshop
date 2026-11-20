package vn.vshop.modules.platform.gateway;

/** Sends one email. Calls the network, so never inside a database transaction. */
public interface EmailSender {
    /** False until a mail server is configured; email notifications are then switched off. */
    boolean configured();

    /** Throws on any delivery failure so the caller can retry later. */
    void send(Message message);

    record Message(String to, String subject, String text, String html) {}
}
