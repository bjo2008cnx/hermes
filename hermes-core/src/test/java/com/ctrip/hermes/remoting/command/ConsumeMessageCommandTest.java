package com.ctrip.hermes.remoting.command;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.unidal.lookup.ComponentTestCase;

import com.ctrip.hermes.core.message.BaseConsumerMessage;
import com.ctrip.hermes.core.message.BrokerConsumerMessage;
import com.ctrip.hermes.core.message.ConsumerMessage;
import com.ctrip.hermes.core.message.ConsumerMessageBatch;
import com.ctrip.hermes.core.message.ProducerMessage;
import com.ctrip.hermes.core.message.codec.MessageCodec;
import com.ctrip.hermes.core.message.codec.MessageCodecFactory;
import com.ctrip.hermes.core.transport.TransferCallback;
import com.ctrip.hermes.core.transport.command.ConsumeMessageCommand;
import com.ctrip.hermes.core.transport.command.Header;

/**
 * @author Leo Liang(jhliang@ctrip.com)
 *
 */
public class ConsumeMessageCommandTest extends ComponentTestCase {

	@Test
	public void testEncodeAndDecode() {
		Map<String, Object> appProperties = new HashMap<>();
		appProperties.put("app_key", 123);

		Map<String, Object> sysProperties = new HashMap<>();
		sysProperties.put("sys_key", "abc");

		List<ProducerMessage<String>> msgs1_1 = new ArrayList<>();
		msgs1_1.add(createProducerMessage("topic1", "t1_body1_1", "t1_key1_1", "t1_p1_1", 1, true, appProperties,
		      sysProperties));
		msgs1_1.add(createProducerMessage("topic1", "t1_body2_1", "t1_key2_1", "t1_p2_1", 1, true, appProperties,
		      sysProperties));
		msgs1_1.add(createProducerMessage("topic1", "t1_body3_1", "t1_key3_1", "t1_p1_1", 1, false, appProperties,
		      sysProperties));

		List<ProducerMessage<String>> msgs1_2 = new ArrayList<>();
		msgs1_2.add(createProducerMessage("topic1", "t1_body1_2", "t1_key1_2", "t1_p1_2", 2, true, appProperties,
		      sysProperties));
		msgs1_2.add(createProducerMessage("topic1", "t1_body2_2", "t1_key2_2", "t1_p2_2", 2, true, appProperties,
		      sysProperties));
		msgs1_2.add(createProducerMessage("topic1", "t1_body3_2", "t1_key3_2", "t1_p1_2", 2, false, appProperties,
		      sysProperties));

		List<ProducerMessage<String>> msgs2 = new ArrayList<>();
		msgs2.add(createProducerMessage("topic2", "t2_body1", "t2_key1", "t2_p1", 1, true, appProperties, sysProperties));
		msgs2.add(createProducerMessage("topic2", "t2_body2", "t2_key2", "t2_p2", 2, true, appProperties, sysProperties));
		msgs2.add(createProducerMessage("topic2", "t2_body3", "t2_key3", "t2_p1", 1, false, appProperties, sysProperties));

		ConsumeMessageCommand cmd = new ConsumeMessageCommand();

		cmd.addMessage(1, createBatch("topic1", msgs1_1, Arrays.asList(1L, 2L, 3L)));
		cmd.addMessage(2, createBatch("topic2", msgs2, Arrays.asList(1L, 2L, 3L)));
		cmd.addMessage(1, createBatch("topic1", msgs1_2, Arrays.asList(1L, 2L, 3L)));

		ByteBuf buf = Unpooled.buffer();
		cmd.toBytes(buf);

		ConsumeMessageCommand decodedCmd = new ConsumeMessageCommand();
		Header header = new Header();
		header.parse(buf);
		decodedCmd.parse(buf, header);

		Map<Long, List<ConsumerMessage<?>>> result = new HashMap<>();
		for (Map.Entry<Long, List<ConsumerMessageBatch>> entry : decodedCmd.getMsgs().entrySet()) {
			long correlationId = entry.getKey();
			List<ConsumerMessageBatch> batches = entry.getValue();

			List<ConsumerMessage<?>> msgs = decodeBatches(batches, String.class);
			result.put(correlationId, msgs);
		}

		Assert.assertEquals(2, result.size());
		Assert.assertTrue(result.containsKey(1L));
		Assert.assertTrue(result.containsKey(2L));

		// batch 1
		List<ConsumerMessage<?>> cmsgList1 = result.get(1L);

		Assert.assertEquals(6, cmsgList1.size());

		for (int i = 0; i < 6; i++) {
			ConsumerMessage<?> cmsg = cmsgList1.get(i);
			Assert.assertEquals("topic1", cmsg.getTopic());
			Assert.assertNotNull(cmsg.getBornTime());
			if (i < 3) {
				Assert.assertTrue(String.format("t1_body%d_1", (i + 1)).equals((String) cmsg.getBody()));
				Assert.assertTrue(String.format("t1_key%d_1", (i + 1)).equals(cmsg.getKey()));
				Assert.assertEquals(Long.valueOf(i + 1).longValue(), ((BrokerConsumerMessage<?>) cmsg).getMsgSeq());
			} else {
				Assert.assertTrue(String.format("t1_body%d_2", (i - 2)).equals((String) cmsg.getBody()));
				Assert.assertTrue(String.format("t1_key%d_2", (i - 2)).equals(cmsg.getKey()));
				Assert.assertEquals(Long.valueOf(i - 2).longValue(), ((BrokerConsumerMessage<?>) cmsg).getMsgSeq());
			}

			Assert.assertEquals(Integer.valueOf(123), cmsg.getProperty("app_key"));
		}
	}

	@SuppressWarnings("rawtypes")
	private List<ConsumerMessage<?>> decodeBatches(List<ConsumerMessageBatch> batches, Class<?> bodyClazz) {
		List<ConsumerMessage<?>> msgs = new ArrayList<>();
		for (ConsumerMessageBatch batch : batches) {
			List<Long> msgSeqs = batch.getMsgSeqs();
			ByteBuf batchData = batch.getData();

			MessageCodec codec = MessageCodecFactory.getCodec(batch.getTopic());

			for (int j = 0; j < msgSeqs.size(); j++) {
				BaseConsumerMessage baseMsg = codec.decode(batchData, bodyClazz);
				BrokerConsumerMessage brokerMsg = new BrokerConsumerMessage(baseMsg);
				brokerMsg.setMsgSeq(msgSeqs.get(j));

				msgs.add(brokerMsg);
			}
		}

		return msgs;
	}

	public <T> ProducerMessage<T> createProducerMessage(String topic, T body, String key, String partition,
	      int partitionNo, boolean priority, Map<String, Object> appProperties, Map<String, Object> sysProperties) {
		ProducerMessage<T> msg = new ProducerMessage<T>(topic, body);
		msg.setBornTime(System.currentTimeMillis());
		msg.setKey(key);
		msg.setPartition(partition);
		msg.setPartitionNo(partitionNo);
		msg.setPriority(priority);
		msg.setAppProperties(appProperties);
		msg.setSysProperties(sysProperties);

		return msg;
	}

	private ConsumerMessageBatch createBatch(String topic, List<ProducerMessage<String>> msgs, List<Long> msgSeqs) {
		ConsumerMessageBatch batch = new ConsumerMessageBatch();

		batch.setTopic(topic);
		batch.addMsgSeqs(msgSeqs);

		final ByteBuf buf = Unpooled.buffer();

		MessageCodec codec = MessageCodecFactory.getCodec(topic);

		for (ProducerMessage<String> msg : msgs) {
			codec.encode(msg, buf);
		}

		batch.setTransferCallback(new TransferCallback() {

			@Override
			public void transfer(ByteBuf out) {
				out.writeBytes(buf);
			}
		});

		return batch;
	}
}