module net.javacrumbs.shedlock.provider.redis.spring {
    requires transitive net.javacrumbs.shedlock.core;
    requires org.slf4j;
    requires spring.data.redis;
    exports net.javacrumbs.shedlock.provider.redis.spring;
}