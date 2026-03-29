CREATE TABLE match_chat_analytics
(
	active_days             INTEGER NOT NULL,
	audio_duration_ms_total BIGINT  NOT NULL,
	created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
	first_message_at        TIMESTAMP WITH TIME ZONE,
	first_reply_at          TIMESTAMP WITH TIME ZONE,
	first_reply_latency_ms  BIGINT,
	last_message_at         TIMESTAMP WITH TIME ZONE,
	matched_at              TIMESTAMP WITH TIME ZONE NOT NULL,
	total_messages          BIGINT                   NOT NULL,
	unmatched_at            TIMESTAMP WITH TIME ZONE,
	updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
	version                 BIGINT                   NOT NULL,
	video_duration_ms_total BIGINT                   NOT NULL,
	first_message_sender_id UUID,
	last_message_sender_id  UUID,
	profile1_id             UUID                     NOT NULL,
	profile2_id             UUID                     NOT NULL,
	CONSTRAINT match_chat_analytics_pkey PRIMARY KEY (profile1_id, profile2_id)
);

CREATE TABLE matches
(
	status       SMALLINT NOT NULL,
	created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
	matched_at   TIMESTAMP WITH TIME ZONE NOT NULL,
	unmatched_at TIMESTAMP WITH TIME ZONE,
	updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
	version      BIGINT                   NOT NULL,
	profile1_id  UUID                     NOT NULL,
	profile2_id  UUID                     NOT NULL,
	CONSTRAINT matches_pkey PRIMARY KEY (profile1_id, profile2_id)
);

CREATE TABLE conversations
(
	conversation_id UUID NOT NULL,
	participant1_id UUID NOT NULL,
	participant2_id UUID NOT NULL,
	status          VARCHAR(255),
	CONSTRAINT conversations_pkey PRIMARY KEY (conversation_id),
	CONSTRAINT ux_conversations_participants UNIQUE (participant1_id, participant2_id)
);

CREATE TABLE messages
(
	created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
	updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
	client_message_id UUID                     NOT NULL,
	conversation_id   UUID                     NOT NULL,
	message_id        UUID                     NOT NULL,
	sender_id         UUID                     NOT NULL,
	type              VARCHAR(16)              NOT NULL,
	text              VARCHAR(5000),
	CONSTRAINT messages_pkey PRIMARY KEY (message_id),
	CONSTRAINT ux_messages_sender_client_id UNIQUE (sender_id, client_message_id),
	CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (conversation_id)
);

CREATE TABLE message_attachments
(
	height        INTEGER,
	width         INTEGER,
	created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
	duration_ms   BIGINT,
	size_bytes    BIGINT                   NOT NULL,
	updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
	attachment_id UUID                     NOT NULL,
	message_id    UUID                     NOT NULL,
	sha256        VARCHAR(64),
	mime_type     VARCHAR(128)             NOT NULL,
	storage_key   VARCHAR(1024)            NOT NULL,
	url           VARCHAR(2048)            NOT NULL,
	original_name VARCHAR(255),
	CONSTRAINT message_attachments_pkey PRIMARY KEY (attachment_id),
	CONSTRAINT fk_message_attachments_message FOREIGN KEY (message_id) REFERENCES messages (message_id)
);

CREATE INDEX idx_mca_matched_at ON match_chat_analytics (matched_at);
CREATE INDEX idx_mca_last_message_at ON match_chat_analytics (last_message_at);
CREATE INDEX idx_mca_profile1_last_message_at ON match_chat_analytics (profile1_id, last_message_at);
CREATE INDEX idx_mca_profile2_last_message_at ON match_chat_analytics (profile2_id, last_message_at);

