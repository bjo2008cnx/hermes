package com.ctrip.hermes.metaserver.meta;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ctrip.hermes.core.utils.CollectionUtil;
import com.ctrip.hermes.meta.entity.Endpoint;
import com.ctrip.hermes.meta.entity.Meta;
import com.ctrip.hermes.meta.entity.Partition;
import com.ctrip.hermes.meta.entity.Server;
import com.ctrip.hermes.meta.entity.Topic;
import com.ctrip.hermes.meta.transform.DefaultSaxParser;

public class MetaMerger {

	private final static Logger log = LoggerFactory.getLogger(MetaMerger.class);

	public Meta merge(Meta base, List<Server> newServers, Map<String, Map<Integer, Endpoint>> newPartition2Endpoint) {
		Meta newMeta;
		try {
			newMeta = DefaultSaxParser.parse(base.toString());
		} catch (Exception e) {
			throw new RuntimeException("Error parse Meta xml");
		}

		if (CollectionUtil.isNotEmpty(newServers)) {
			newMeta.getServers().clear();
			for (Server s : newServers) {
				newMeta.addServer(s);
			}
		}

		if (CollectionUtil.isNotEmpty(newPartition2Endpoint)) {
			removeBrokerEndpoints(newMeta);

			for (Map.Entry<String, Map<Integer, Endpoint>> topicEntry : newPartition2Endpoint.entrySet()) {
				Topic topic = newMeta.findTopic(topicEntry.getKey());

				List<Partition> newPartitions = new ArrayList<>();
				for (Map.Entry<Integer, Endpoint> partitionEntry : topicEntry.getValue().entrySet()) {
					Endpoint endpoint = partitionEntry.getValue();
					newMeta.addEndpoint(endpoint);

					int partitionId = partitionEntry.getKey();
					Partition p = topic.findPartition(partitionId);
					if (p == null) {
						log.warn("partition {} not found in topic {}, ignore", partitionId, topic);
					} else {
						p.setEndpoint(endpoint.getId());
						newPartitions.add(p);
					}
				}

				topic.getPartitions().clear();
				topic.getPartitions().addAll(newPartitions);
			}
		}

		return newMeta;

	}

	private void removeBrokerEndpoints(Meta base) {
		Map<String, Endpoint> endpoints = base.getEndpoints();
		Iterator<Entry<String, Endpoint>> iter = endpoints.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<String, Endpoint> entry = iter.next();
			if (Endpoint.BROKER.equals(entry.getValue().getType())) {
				iter.remove();
			}
		}
	}

}
