package com.flowyun.dolphinmq.executor;

import com.flowyun.dolphinmq.consumer.PullConsumerClient;

/**
 * @author Barry
 */
public class CheckPendingListScheduledExecutor implements Runnable {

    private PullConsumerClient client;

    CheckPendingListScheduledExecutor() {

    }

    public CheckPendingListScheduledExecutor(PullConsumerClient client) {
        this.client = client;
    }

    @Override
    public void run() {
        client.checkPendingList();
    }
}