package net.javacrumbs.shedlock.provider.couchbase.mock;

import com.couchbase.client.core.ClusterFacade;
import com.couchbase.client.core.message.internal.PingReport;
import com.couchbase.client.core.service.ServiceType;
import com.couchbase.client.java.AsyncBucket;
import com.couchbase.client.java.PersistTo;
import com.couchbase.client.java.ReplicaMode;
import com.couchbase.client.java.ReplicateTo;
import com.couchbase.client.java.analytics.AnalyticsQuery;
import com.couchbase.client.java.analytics.AnalyticsQueryResult;
import com.couchbase.client.java.bucket.BucketManager;
import com.couchbase.client.java.datastructures.MutationOptionBuilder;
import com.couchbase.client.java.document.Document;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.JsonLongDocument;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import com.couchbase.client.java.error.DocumentAlreadyExistsException;
import com.couchbase.client.java.error.DocumentDoesNotExistException;
import com.couchbase.client.java.query.N1qlQuery;
import com.couchbase.client.java.query.N1qlQueryResult;
import com.couchbase.client.java.query.Statement;
import com.couchbase.client.java.repository.Repository;
import com.couchbase.client.java.search.SearchQuery;
import com.couchbase.client.java.search.result.SearchQueryResult;
import com.couchbase.client.java.subdoc.LookupInBuilder;
import com.couchbase.client.java.subdoc.MutateInBuilder;
import com.couchbase.client.java.view.SpatialViewQuery;
import com.couchbase.client.java.view.SpatialViewResult;
import com.couchbase.client.java.view.ViewQuery;
import com.couchbase.client.java.view.ViewResult;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;


public class Bucket implements com.couchbase.client.java.Bucket {
    static final String LOCK_NAME = "name";
    ReentrantLock lock = new ReentrantLock();

    Map<String, JsonDocument> documents = new ConcurrentHashMap<>();

    @Override
    public <D extends Document<?>> D insert(D d) {
        synchronized (this) {
            sleep();
            String name = ((JsonDocument) d).content().getString(LOCK_NAME);
            if (documents.get(name) == null) {
                if (documents.get(name) == null) {
                    documents.put(name, (JsonDocument) d);
                }
                return d;
            } else {
                throw new DocumentAlreadyExistsException();
            }
        }
    }

    @Override
    public <D extends Document<?>> D replace(D d) {
        synchronized (this) {
            sleep();
            String name = ((JsonDocument) d).content().getString(LOCK_NAME);
            JsonDocument document;
            document = documents.get(name);
            if (document == null) {
                throw new DocumentDoesNotExistException();
            }
            documents.replace(name, (JsonDocument) d);
            return d;
        }
    }

    @Override
    public JsonDocument get(String s) {
        return documents.get(s);
    }

    private void sleep() {
        try {
            Thread.sleep(6);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public AsyncBucket async() {
        return null;
    }

    @Override
    public ClusterFacade core() {
        return null;
    }

    @Override
    public CouchbaseEnvironment environment() {
        return null;
    }

    @Override
    public String name() {
        return null;
    }


    @Override
    public JsonDocument get(String s, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D get(D d) {
        return null;
    }

    @Override
    public <D extends Document<?>> D get(D d, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D get(String s, Class<D> aClass) {
        return null;
    }

    @Override
    public <D extends Document<?>> D get(String s, Class<D> aClass, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public boolean exists(String s) {
        return false;
    }

    @Override
    public boolean exists(String s, long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public <D extends Document<?>> boolean exists(D d) {
        return false;
    }

    @Override
    public <D extends Document<?>> boolean exists(D d, long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public List<JsonDocument> getFromReplica(String s, ReplicaMode replicaMode) {
        return null;
    }

    @Override
    public Iterator<JsonDocument> getFromReplica(String s) {
        return null;
    }

    @Override
    public List<JsonDocument> getFromReplica(String s, ReplicaMode replicaMode, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public Iterator<JsonDocument> getFromReplica(String s, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> List<D> getFromReplica(D d, ReplicaMode replicaMode) {
        return null;
    }

    @Override
    public <D extends Document<?>> Iterator<D> getFromReplica(D d) {
        return null;
    }

    @Override
    public <D extends Document<?>> List<D> getFromReplica(D d, ReplicaMode replicaMode, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> Iterator<D> getFromReplica(D d, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> List<D> getFromReplica(String s, ReplicaMode replicaMode, Class<D> aClass) {
        return null;
    }

    @Override
    public <D extends Document<?>> Iterator<D> getFromReplica(String s, Class<D> aClass) {
        return null;
    }

    @Override
    public <D extends Document<?>> List<D> getFromReplica(String s, ReplicaMode replicaMode, Class<D> aClass, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> Iterator<D> getFromReplica(String s, Class<D> aClass, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public JsonDocument getAndLock(String s, int i) {
        return null;
    }

    @Override
    public JsonDocument getAndLock(String s, int i, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D getAndLock(D d, int i) {
        return null;
    }

    @Override
    public <D extends Document<?>> D getAndLock(D d, int i, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D getAndLock(String s, int i, Class<D> aClass) {
        return null;
    }

    @Override
    public <D extends Document<?>> D getAndLock(String s, int i, Class<D> aClass, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public JsonDocument getAndTouch(String s, int i) {
        return null;
    }

    @Override
    public JsonDocument getAndTouch(String s, int i, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D getAndTouch(D d) {
        return null;
    }

    @Override
    public <D extends Document<?>> D getAndTouch(D d, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D getAndTouch(String s, int i, Class<D> aClass) {
        return null;
    }

    @Override
    public <D extends Document<?>> D getAndTouch(String s, int i, Class<D> aClass, long l, TimeUnit timeUnit) {
        return null;
    }


    @Override
    public <D extends Document<?>> D insert(D d, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D insert(D d, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> D insert(D d, PersistTo persistTo, ReplicateTo replicateTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D insert(D d, PersistTo persistTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> D insert(D d, PersistTo persistTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D insert(D d, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> D insert(D d, ReplicateTo replicateTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D upsert(D d) {
        return null;
    }

    @Override
    public <D extends Document<?>> D upsert(D d, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D upsert(D d, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> D upsert(D d, PersistTo persistTo, ReplicateTo replicateTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D upsert(D d, PersistTo persistTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> D upsert(D d, PersistTo persistTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D upsert(D d, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> D upsert(D d, ReplicateTo replicateTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D replace(D d, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D replace(D d, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> D replace(D d, PersistTo persistTo, ReplicateTo replicateTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D replace(D d, PersistTo persistTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> D replace(D d, PersistTo persistTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D replace(D d, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> D replace(D d, ReplicateTo replicateTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D remove(D d) {
        return null;
    }

    @Override
    public <D extends Document<?>> D remove(D d, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D remove(D d, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> D remove(D d, PersistTo persistTo, ReplicateTo replicateTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D remove(D d, PersistTo persistTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> D remove(D d, PersistTo persistTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D remove(D d, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> D remove(D d, ReplicateTo replicateTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public JsonDocument remove(String s) {
        return null;
    }

    @Override
    public JsonDocument remove(String s, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public JsonDocument remove(String s, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public JsonDocument remove(String s, PersistTo persistTo, ReplicateTo replicateTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public JsonDocument remove(String s, PersistTo persistTo) {
        return null;
    }

    @Override
    public JsonDocument remove(String s, PersistTo persistTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public JsonDocument remove(String s, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public JsonDocument remove(String s, ReplicateTo replicateTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D remove(String s, Class<D> aClass) {
        return null;
    }

    @Override
    public <D extends Document<?>> D remove(String s, Class<D> aClass, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D remove(String s, PersistTo persistTo, ReplicateTo replicateTo, Class<D> aClass) {
        return null;
    }

    @Override
    public <D extends Document<?>> D remove(String s, PersistTo persistTo, ReplicateTo replicateTo, Class<D> aClass, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D remove(String s, PersistTo persistTo, Class<D> aClass) {
        return null;
    }

    @Override
    public <D extends Document<?>> D remove(String s, PersistTo persistTo, Class<D> aClass, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D remove(String s, ReplicateTo replicateTo, Class<D> aClass) {
        return null;
    }

    @Override
    public <D extends Document<?>> D remove(String s, ReplicateTo replicateTo, Class<D> aClass, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public ViewResult query(ViewQuery viewQuery) {
        return null;
    }

    @Override
    public SpatialViewResult query(SpatialViewQuery spatialViewQuery) {
        return null;
    }

    @Override
    public ViewResult query(ViewQuery viewQuery, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public SpatialViewResult query(SpatialViewQuery spatialViewQuery, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public N1qlQueryResult query(Statement statement) {
        return null;
    }

    @Override
    public N1qlQueryResult query(Statement statement, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public N1qlQueryResult query(N1qlQuery n1qlQuery) {
        return null;
    }

    @Override
    public N1qlQueryResult query(N1qlQuery n1qlQuery, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public SearchQueryResult query(SearchQuery searchQuery) {
        return null;
    }

    @Override
    public SearchQueryResult query(SearchQuery searchQuery, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public AnalyticsQueryResult query(AnalyticsQuery analyticsQuery) {
        return null;
    }

    @Override
    public AnalyticsQueryResult query(AnalyticsQuery analyticsQuery, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public Boolean unlock(String s, long l) {
        return null;
    }

    @Override
    public Boolean unlock(String s, long l, long l1, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> Boolean unlock(D d) {
        return null;
    }

    @Override
    public <D extends Document<?>> Boolean unlock(D d, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public Boolean touch(String s, int i) {
        return null;
    }

    @Override
    public Boolean touch(String s, int i, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> Boolean touch(D d) {
        return null;
    }

    @Override
    public <D extends Document<?>> Boolean touch(D d, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, PersistTo persistTo) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, long l1, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, PersistTo persistTo, long l1, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, ReplicateTo replicateTo, long l1, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, PersistTo persistTo, ReplicateTo replicateTo, long l1, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, long l1) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, long l1, PersistTo persistTo) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, long l1, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, long l1, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, long l1, long l2, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, long l1, PersistTo persistTo, long l2, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, long l1, ReplicateTo replicateTo, long l2, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, long l1, PersistTo persistTo, ReplicateTo replicateTo, long l2, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, long l1, int i) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, long l1, int i, PersistTo persistTo) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, long l1, int i, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, long l1, int i, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, long l1, int i, long l2, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, long l1, int i, PersistTo persistTo, long l2, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, long l1, int i, ReplicateTo replicateTo, long l2, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public JsonLongDocument counter(String s, long l, long l1, int i, PersistTo persistTo, ReplicateTo replicateTo, long l2, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D append(D d) {
        return null;
    }

    @Override
    public <D extends Document<?>> D append(D d, PersistTo persistTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> D append(D d, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> D append(D d, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> D append(D d, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D append(D d, PersistTo persistTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D append(D d, ReplicateTo replicateTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D append(D d, PersistTo persistTo, ReplicateTo replicateTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D prepend(D d) {
        return null;
    }

    @Override
    public <D extends Document<?>> D prepend(D d, PersistTo persistTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> D prepend(D d, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> D prepend(D d, PersistTo persistTo, ReplicateTo replicateTo) {
        return null;
    }

    @Override
    public <D extends Document<?>> D prepend(D d, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D prepend(D d, PersistTo persistTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D prepend(D d, ReplicateTo replicateTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <D extends Document<?>> D prepend(D d, PersistTo persistTo, ReplicateTo replicateTo, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public LookupInBuilder lookupIn(String s) {
        return null;
    }

    @Override
    public MutateInBuilder mutateIn(String s) {
        return null;
    }

    @Override
    public <V> boolean mapAdd(String s, String s1, V v) {
        return false;
    }

    @Override
    public <V> boolean mapAdd(String s, String s1, V v, long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public <V> boolean mapAdd(String s, String s1, V v, MutationOptionBuilder mutationOptionBuilder) {
        return false;
    }

    @Override
    public <V> boolean mapAdd(String s, String s1, V v, MutationOptionBuilder mutationOptionBuilder, long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public <V> V mapGet(String s, String s1, Class<V> aClass) {
        return null;
    }

    @Override
    public <V> V mapGet(String s, String s1, Class<V> aClass, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public boolean mapRemove(String s, String s1) {
        return false;
    }

    @Override
    public boolean mapRemove(String s, String s1, long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public boolean mapRemove(String s, String s1, MutationOptionBuilder mutationOptionBuilder) {
        return false;
    }

    @Override
    public boolean mapRemove(String s, String s1, MutationOptionBuilder mutationOptionBuilder, long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public int mapSize(String s) {
        return 0;
    }

    @Override
    public int mapSize(String s, long l, TimeUnit timeUnit) {
        return 0;
    }

    @Override
    public <E> E listGet(String s, int i, Class<E> aClass) {
        return null;
    }

    @Override
    public <E> E listGet(String s, int i, Class<E> aClass, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <E> boolean listAppend(String s, E e) {
        return false;
    }

    @Override
    public <E> boolean listAppend(String s, E e, long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public <E> boolean listAppend(String s, E e, MutationOptionBuilder mutationOptionBuilder) {
        return false;
    }

    @Override
    public <E> boolean listAppend(String s, E e, MutationOptionBuilder mutationOptionBuilder, long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public boolean listRemove(String s, int i) {
        return false;
    }

    @Override
    public boolean listRemove(String s, int i, long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public boolean listRemove(String s, int i, MutationOptionBuilder mutationOptionBuilder) {
        return false;
    }

    @Override
    public boolean listRemove(String s, int i, MutationOptionBuilder mutationOptionBuilder, long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public <E> boolean listPrepend(String s, E e) {
        return false;
    }

    @Override
    public <E> boolean listPrepend(String s, E e, long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public <E> boolean listPrepend(String s, E e, MutationOptionBuilder mutationOptionBuilder) {
        return false;
    }

    @Override
    public <E> boolean listPrepend(String s, E e, MutationOptionBuilder mutationOptionBuilder, long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public <E> boolean listSet(String s, int i, E e) {
        return false;
    }

    @Override
    public <E> boolean listSet(String s, int i, E e, long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public <E> boolean listSet(String s, int i, E e, MutationOptionBuilder mutationOptionBuilder) {
        return false;
    }

    @Override
    public <E> boolean listSet(String s, int i, E e, MutationOptionBuilder mutationOptionBuilder, long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public int listSize(String s) {
        return 0;
    }

    @Override
    public int listSize(String s, long l, TimeUnit timeUnit) {
        return 0;
    }

    @Override
    public <E> boolean setAdd(String s, E e) {
        return false;
    }

    @Override
    public <E> boolean setAdd(String s, E e, long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public <E> boolean setAdd(String s, E e, MutationOptionBuilder mutationOptionBuilder) {
        return false;
    }

    @Override
    public <E> boolean setAdd(String s, E e, MutationOptionBuilder mutationOptionBuilder, long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public <E> boolean setContains(String s, E e) {
        return false;
    }

    @Override
    public <E> boolean setContains(String s, E e, long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public <E> E setRemove(String s, E e) {
        return null;
    }

    @Override
    public <E> E setRemove(String s, E e, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <E> E setRemove(String s, E e, MutationOptionBuilder mutationOptionBuilder) {
        return null;
    }

    @Override
    public <E> E setRemove(String s, E e, MutationOptionBuilder mutationOptionBuilder, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public int setSize(String s) {
        return 0;
    }

    @Override
    public int setSize(String s, long l, TimeUnit timeUnit) {
        return 0;
    }

    @Override
    public <E> boolean queuePush(String s, E e) {
        return false;
    }

    @Override
    public <E> boolean queuePush(String s, E e, long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public <E> boolean queuePush(String s, E e, MutationOptionBuilder mutationOptionBuilder) {
        return false;
    }

    @Override
    public <E> boolean queuePush(String s, E e, MutationOptionBuilder mutationOptionBuilder, long l, TimeUnit timeUnit) {
        return false;
    }

    @Override
    public <E> E queuePop(String s, Class<E> aClass) {
        return null;
    }

    @Override
    public <E> E queuePop(String s, Class<E> aClass, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <E> E queuePop(String s, Class<E> aClass, MutationOptionBuilder mutationOptionBuilder) {
        return null;
    }

    @Override
    public <E> E queuePop(String s, Class<E> aClass, MutationOptionBuilder mutationOptionBuilder, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public int queueSize(String s) {
        return 0;
    }

    @Override
    public int queueSize(String s, long l, TimeUnit timeUnit) {
        return 0;
    }

    @Override
    public int invalidateQueryCache() {
        return 0;
    }

    @Override
    public BucketManager bucketManager() {
        return null;
    }

    @Override
    public Repository repository() {
        return null;
    }

    @Override
    public Boolean close() {
        return null;
    }

    @Override
    public Boolean close(long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public PingReport ping(String s) {
        return null;
    }

    @Override
    public PingReport ping(String s, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public PingReport ping() {
        return null;
    }

    @Override
    public PingReport ping(long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public PingReport ping(Collection<ServiceType> collection) {
        return null;
    }

    @Override
    public PingReport ping(Collection<ServiceType> collection, long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public PingReport ping(String s, Collection<ServiceType> collection) {
        return null;
    }

    @Override
    public PingReport ping(String s, Collection<ServiceType> collection, long l, TimeUnit timeUnit) {
        return null;
    }
}
