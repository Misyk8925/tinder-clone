package com.tinder.match.conversation.controller;

import com.tinder.match.conversation.websocket.GlobalMessagesWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/rest/ws/global")
@RequiredArgsConstructor
public class WebSocketDebugController {

    private final GlobalMessagesWebSocketHandler globalMessagesWebSocketHandler;

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "connectedCount", globalMessagesWebSocketHandler.connectedCount(),
                "hasConnections", globalMessagesWebSocketHandler.hasConnections()
        );
    }
}
