package net.javacrumbs.shedlock.provider.hazelcast4;

import com.hazelcast.map.IMap;

/**
 * Interface that allows you to change the default getMap() behavior for example by implementing reconnect.
 */
public interface HazelcastMapSource {
    IMap<String, HazelcastLock> getMap();
}
