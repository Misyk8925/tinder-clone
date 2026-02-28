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
        messagingTemplate.convertAndSend("/topic/conversations/" + e.conversationId(), e);
        messagingTemplate.convertAndSend("/topic/messages", e);
    }
}
