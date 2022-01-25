package net.javacrumbs.shedlock.provider.memcached;

import net.javacrumbs.shedlock.core.ClockProvider;
import net.spy.memcached.AddrUtil;
import net.spy.memcached.MemcachedClient;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

public class TestMain {


    public static void main(String[] args) throws IOException {
//        MemcachedClient client = new MemcachedClient(AddrUtil.getAddresses("127.0.0.1:11211"));
////        25920000
////        25910000
//        int c = 60*60*24*30;
//        System.out.println(c);
//        client.set("1222",  c, "wwww2");
//        Object cc =  client.get("1222");
//        System.out.println(cc);
        getSecondUntil(Instant.now());
    }

    private static long getSecondUntil(Instant instant) {
        return Duration.between(ClockProvider.now(), instant).toMillis();
    }
}
