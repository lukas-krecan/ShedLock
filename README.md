# ShedLock

You have a simple Spring scheduler which works great until you need to run the application
on multiple instances. Now what? You want to execute your tasks only once. You can use Quartz, 
but it's incredibly complex. You can use environment variable to determine a "scheduler master",
but what if it dies? Or you can use ShedLock.

ShedLock does one and only thing. It makes sure your tasks ar executed at most once. It coordinates
cluster nodes using shared database. If a task is being executed on one node, it acquires a lock which
prevents execution of the same task from another node (or thread). 
 
Since this is the very first version, only Spring annotated tasks coordinated through Mongo are supported. More
scheduling and coordination mechanisms and expected in the future. 

## Usage
### Annotate your scheduled tasks
 
    @Scheduled( ... )
    @SchedulerLock(name = "reportCurrentTime")
    public void scheduledTasks() {
        // do something
    }
        
The `@SchedulerLock` annotation has several purposes. First of all, only annotated methods are locked, the library ignores
all other scheduled tasks. You also have to specify the name for the lock. Only one tasks with the same name can be executed
at the same time. Lastly, you can set `lockForMillis` attribute which specifies how long the lock should be kept in case the
executing node dies. This is just a fallback, under normal circumstances the lock is released as soon the tasks finishes.

### Configure the task scheduler
Now we need to integrate the library into Spring. It's done by wrapping standard Spring task scheduler.  

    @Bean
    public TaskScheduler taskScheduler(LockProvider lockProvider) {
        int poolSize = 10;
        return SpringLockableTaskSchedulerFactory.newLockableTaskScheduler(poolSize, lockProvider);
    }

### Configure LockProvider
#### Mongo

    @Bean
    public LockProvider lockProvider(MongoClient mongo) {
        return new MongoLockProvider(mongo, "databaseName");
    }

Maven

min versions

error handling
