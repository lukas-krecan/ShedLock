package net.javacrumbs.shedlock.test.boot;

import org.springframework.stereotype.Component;

@Component
public class NameSource {
    public String getLockName() {
        return "reportCurrentTime";
    }
}
