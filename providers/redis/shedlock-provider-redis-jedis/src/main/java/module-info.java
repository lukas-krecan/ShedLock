module net.javacrumbs.shedlock.provider.redis.jedis {
    requires transitive net.javacrumbs.shedlock.core;
    requires org.slf4j;
    requires jedis;
    exports net.javacrumbs.shedlock.provider.redis.jedis;
}