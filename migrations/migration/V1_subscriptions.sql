CREATE TABLE stripe_event_inbox
(
	attempts       INTEGER                  NOT NULL,
	livemode       BOOLEAN                  NOT NULL,
	next_retry_at  TIMESTAMP WITH TIME ZONE,
	processed_at   TIMESTAMP WITH TIME ZONE,
	stripe_created BIGINT,
	event_type     VARCHAR(255)             NOT NULL,
	id             VARCHAR(255)             NOT NULL,
	last_error     VARCHAR(255),
	object_id      VARCHAR(255),
	payload_json   TEXT                     NOT NULL,
	status         VARCHAR(255),
	CONSTRAINT stripe_event_inbox_pkey PRIMARY KEY (id)
);

CREATE TABLE billing_subscriptions
(
	cancel_at_period_end      BOOLEAN                  NOT NULL,
	current_period_end        TIMESTAMP WITH TIME ZONE,
	last_stripe_event_created BIGINT,
	updated_at                TIMESTAMP WITH TIME ZONE,
	price_id                  VARCHAR(255),
	status                    VARCHAR(255),
	stripe_customer_id        VARCHAR(255)             NOT NULL,
	stripe_subscription_id    VARCHAR(255)             NOT NULL,
	user_id                   VARCHAR(255)             NOT NULL,
	CONSTRAINT billing_subscriptions_pkey PRIMARY KEY (stripe_subscription_id)
);

CREATE TABLE stripe_customer
(
	livemode           BOOLEAN,
	created_at         TIMESTAMP WITH TIME ZONE,
	updated_at         TIMESTAMP WITH TIME ZONE,
	id                 VARCHAR(255) NOT NULL,
	stripe_customer_id VARCHAR(255),
	user_id            VARCHAR(255),
	CONSTRAINT stripe_customer_pkey PRIMARY KEY (id)
);

