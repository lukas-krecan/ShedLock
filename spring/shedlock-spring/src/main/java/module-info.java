module net.javacrumbs.shedlock.spring {
    requires net.javacrumbs.shedlock.core;
    requires spring.beans;
    requires spring.context;
    requires spring.core;
    requires spring.aop;
    requires org.slf4j;
    opens net.javacrumbs.shedlock.spring.aop to spring.beans, spring.core;
    exports net.javacrumbs.shedlock.spring.annotation;
    exports net.javacrumbs.shedlock.spring;
    exports net.javacrumbs.shedlock.spring.aop to spring.context;
}
