package com.ctrip.hermes.adapter;


import com.ctrip.cmessaging.client.IMessage;
import com.ctrip.cmessaging.client.ISyncConsumer;
import com.ctrip.cmessaging.client.exception.ConsumeTimeoutException;
import com.ctrip.cmessaging.client.exception.IllegalExchangeName;
import com.ctrip.cmessaging.client.exception.IllegalTopic;
import com.ctrip.cmessaging.client.impl.ConsumerFactory;
import com.google.common.base.Charsets;

public class CmessageConsumerSync {

	public static void main(String[] args) throws IllegalTopic, IllegalExchangeName, InterruptedException {


		/**
		 * change "com.ctrip.cmessaging.client.impl.ConsumerFactory" to "com.ctrip.hermes.adapter.impl.ConsumerFactory"
		 */
		ISyncConsumer consumer1 = ConsumerFactory.instance.createConsumerAsSync("922101_9dc4a4ff", "order_new",
				  "ExchangeTest", 3000);
		consumer1.setBatchSize(20);
		while (true) {
			try {
				IMessage message1 = consumer1.consumeOne();
				System.out.println("Body:" + new String(message1.getBody(), Charsets.UTF_8));
				System.out.println(message1);
				//业务逻辑
				//
				//根据业务需求设置Ack或Nack
				//message1.setAcks(AckMode.Ack);
//				message1.setAcks(AckMode.Nack);
				message1.dispose();
			} catch (ConsumeTimeoutException e) {
				System.out.println("ConsumeTimeoutException");
			}
		}
	}
}
