/**
 * Copyright 2009-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.geode;

import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionContext;
import org.apache.geode.distributed.DistributedLockService;
import org.apache.geode.distributed.DistributedSystem;
import org.apache.geode.distributed.LeaseExpiredException;
import org.apache.geode.distributed.internal.locks.DLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Function which executes on a Server Pool to lock a shared Resource
 * using the Distributed Lock Service of Geode which is fault tolerant
 * across the Geode Cluster
 */

public class DistributedLockFunction implements Function {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockFunction.class);

    private static final long serialVersionUID = 3995151973953236487L;

    private static DLockService dls;

    public static synchronized DLockService getInstance(DistributedSystem distributedSystem){
        if(dls == null){
            dls = (DLockService) DLockService.getServiceNamed(Constants.DISTRIBUTED_LOCK_SERVICE_NAME);
            if(dls == null){
                dls = (DLockService) DLockService.create(Constants.DISTRIBUTED_LOCK_SERVICE_NAME,distributedSystem);
            }
        }
        return dls;
    }

    @Override
    public void execute(FunctionContext functionContext) {
        try{
            DLockService instance = getInstance(functionContext.getCache().getDistributedSystem());
            Object [] arr = (Object[]) functionContext.getArguments();
            String lockName = String.valueOf(arr[0]);
            String operation = String.valueOf(arr[1]);
            long lease = (long) arr[2];
            if(Constants.LOCK.equalsIgnoreCase(operation)){
                boolean lock = instance.lock(lockName,0,lease,false,true,false);
                if(lock){
                    functionContext.getResultSender().lastResult(true);
                } else {
                    functionContext.getResultSender().lastResult(false);
                }
            } else if(Constants.UNLOCK.equalsIgnoreCase(operation)){

                try{
                    instance.unlock(lockName);
                }catch(LeaseExpiredException e){
                    log.debug("{} unlock - it is already unlocked",lockName);
                }
                log.info("{} unlocked successfully",lockName);
                functionContext.getResultSender().lastResult(true);
            }
        } catch(Exception e){
            log.error("Unable to execute Distributed Lock Function {} " ,e);
            functionContext.getResultSender().sendException(e);
        }
    }

    @Override
    public boolean isHA() {
        return true;
    }

    @Override
    public boolean hasResult() {
        return true;
    }

    @Override
    public boolean optimizeForWrite() {
        return true;
    }

    @Override
    public String getId() {
        return DistributedLockFunction.class.getName();
    }

}
