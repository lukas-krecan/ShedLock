module net.javacrumbs.shedlock.spring {
    requires org.slf4j;
    requires net.javacrumbs.shedlock.core;
    requires spring.beans;
    requires spring.context;
    requires spring.core;
    requires spring.aop;
    opens net.javacrumbs.shedlock.spring.aop to spring.beans, spring.core, spring.context;
    opens net.javacrumbs.shedlock.spring to spring.beans, spring.core, spring.context;
}
