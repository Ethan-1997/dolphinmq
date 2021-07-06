package com.flowyun.dolphinmq.executor;

import com.flowyun.dolphinmq.consumer.PullConsumerClient;

/**
 * @author Barry
 */
public class PullHealthyMessagesScheduledExecutor implements Runnable {

    private PullConsumerClient client;

    PullHealthyMessagesScheduledExecutor() {

    }

    public PullHealthyMessagesScheduledExecutor(PullConsumerClient client) {
        this.client = client;
    }

    @Override
    public void run() {
        client.consumeHealthMessages();
    }
}