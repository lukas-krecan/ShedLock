module net.javacrumbs.shedlock.provider.hazelcast {
    requires transitive net.javacrumbs.shedlock.core;
    requires org.slf4j;
    requires hazelcast;
    exports net.javacrumbs.shedlock.provider.hazelcast;
}