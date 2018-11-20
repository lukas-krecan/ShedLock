module net.javacrumbs.shedlock.provider.couchbase.javaclient {
    requires transitive net.javacrumbs.shedlock.core;
    requires org.slf4j;
    requires com.couchbase.client.java;
    exports net.javacrumbs.shedlock.provider.couchbase.javaclient;
}