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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DistributedLockFunction implements Function {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockFunction.class);

    private static final long serialVersionUID = 3995151973953236487L;

    private static DistributedLockService dls;

    public static synchronized DistributedLockService getInstance(DistributedSystem distributedSystem){
        if(dls == null){
            dls = DistributedLockService.getServiceNamed("distributedLockService");
            if(dls == null){
                dls = DistributedLockService.create("distributedLockService",distributedSystem);
            }
        }
        return dls;
    }

    @Override
    public void execute(FunctionContext functionContext) {
        try{
            DistributedLockService instance = getInstance(functionContext.getCache().getDistributedSystem());
            Object [] arr = (Object[]) functionContext.getArguments();
            String lockName = String.valueOf(arr[0]);
            String operation = String.valueOf(arr[1]);
            long lease = (long) arr[2];
            if(Constants.LOCK.equalsIgnoreCase(operation)){
                boolean lock = instance.lock(lockName, 0, lease);
                log.info("{} Lock acquired by {}",lockName,functionContext.getMemberName());
                if(lock){
                    functionContext.getResultSender().lastResult(true);
                } else {
                    functionContext.getResultSender().lastResult(false);
                }
            } else if(Constants.UNLOCK.equalsIgnoreCase(operation)){
                try{
                    instance.unlock(lockName);
                }catch(LeaseExpiredException e){
                    log.debug("unlock - it is already unlocked");
                }
                functionContext.getResultSender().lastResult(true);
            }
        } catch(Exception e){
            log.error("Unable to execute function {}" ,e);
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
