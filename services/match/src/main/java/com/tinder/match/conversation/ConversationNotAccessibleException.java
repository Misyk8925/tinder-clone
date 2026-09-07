package com.tinder.match.conversation;

import org.springframework.messaging.MessagingException;

/**
 * The conversation does not exist, or the caller is not one of its participants.
 * <p>
 * Deliberately one exception for both cases, carrying one message: distinguishing them would let
 * a caller probe conversation IDs for existence. It is separate from plain
 * {@link MessagingException} so the two map to different HTTP statuses — a malformed message is a
 * 400, an inaccessible conversation is a 404.
 */
public class ConversationNotAccessibleException extends MessagingException {

    public ConversationNotAccessibleException() {
        super("Conversation not found");
    }
}
