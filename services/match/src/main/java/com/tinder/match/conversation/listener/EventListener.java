package com.tinder.match.conversation.listener;

import com.tinder.match.conversation.event.MessageCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
@Slf4j
public class EventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MessageCreatedEvent e) {
        log.info(
                "Broadcasting message event messageId={} conversationId={} senderId={}",
                e.messageId(),
                e.conversationId(),
                e.senderId()
        );
        // Only the conversation's own topic — subscription to it is restricted to the two
        // participants by ConversationSubscriptionInterceptor. There is deliberately no
        // service-wide "/topic/messages" fan-out: it delivered every private message on the
        // platform to any subscriber.
        messagingTemplate.convertAndSend("/topic/conversations/" + e.conversationId(), e);
    }
}
