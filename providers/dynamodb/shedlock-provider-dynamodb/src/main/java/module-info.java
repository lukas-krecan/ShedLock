module net.javacrumbs.shedlock.provider.dynamodb {
    requires transitive net.javacrumbs.shedlock.core;
    requires org.slf4j;
    requires aws.java.sdk.dynamodb;
    exports net.javacrumbs.shedlock.provider.dynamodb;
}