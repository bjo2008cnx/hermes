package com.ctrip.hermes.local;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.ctrip.hermes.local.pojo.MockConsumer;
import com.ctrip.hermes.local.pojo.MockConsumerGroup;
import com.ctrip.hermes.local.pojo.OutputMessage;
import com.ctrip.hermes.message.StoredMessage;
import com.google.common.collect.ArrayListMultimap;


/**
 * Mock actions of consumer, only for Demo presentation.
 * When local broker is done, this is no logger needed.
 */
public class MockConsumers {

    public static final String SPLITER = "#@#";

    private MockConsumers() {
    }

    static MockConsumers consumers = new MockConsumers();

    public static MockConsumers getInstance() {
        return consumers;
    }

    ArrayListMultimap<String /*topic+group*/, MockConsumer> consumerMap = ArrayListMultimap.create();

    public void putOneConsumer(String topic, String group, String name) {
        consumerMap.put(topic + SPLITER + group, new MockConsumer(name));
    }

    public List<MockConsumerGroup> getAllGroup() {
        List<MockConsumerGroup> groups = new ArrayList<>();
        for (String key : consumerMap.keySet()) {
            String[] topicAndGroup = key.split(SPLITER);
            groups.add(new MockConsumerGroup(topicAndGroup[0], topicAndGroup[1],
                    consumerMap.get(key)));
        }
        return groups;
    }

    public List<MockConsumerGroup> getGroupByTopic(String topic) {
        List<MockConsumerGroup> groups = new ArrayList<>();
        for (String key : consumerMap.keySet()) {
            String[] topicAndGroup = key.split(SPLITER);
            if (topic.equals(topicAndGroup[0])) {
                groups.add(new MockConsumerGroup(topicAndGroup[0], topicAndGroup[1],
                        consumerMap.get(key)));
            }
        }
        return groups;
    }

    public void consumeOneMsg(String topic, String group, StoredMessage<byte[]>  msg) {
        List<MockConsumer> consumers = consumerMap.get(topic + SPLITER + group);

        consumers.get(new Random().nextInt(consumers.size())).consumeOneMsg(msg);
    }
}