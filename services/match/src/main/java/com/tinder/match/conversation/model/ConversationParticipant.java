package com.tinder.match.conversation.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "participants")
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @Column(name = "participant_name")
    private String name;




}
