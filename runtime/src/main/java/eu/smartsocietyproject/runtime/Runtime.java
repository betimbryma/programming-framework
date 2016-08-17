package eu.smartsocietyproject.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import eu.smartsocietyproject.pf.*;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Function;

public class Runtime {
    private static ObjectMapper jsonMapper = new  ObjectMapper();
    private final SmartSocietyApplicationContext context;
    private final Application application;
    private final HashMap<UUID, TaskRunnerDescriptor> runnerDescriptors = new HashMap<>();
    private final ImmutableMap<String, TaskFlowDefinition> flowsByType;
    private final ExecutorService executor = new ThreadPoolExecutor( //can return both Executor and ExecutorService
     30,// the number of threads to keep active in the pool, even if they are idle
     1000,// the maximum number of threads to allow in the pool. After that, the tasks are queued
     1L, TimeUnit.HOURS,// when the number of threads is greater than the core, this is the maximum time that excess idle threads will wait for new tasks before terminating.
     new LinkedBlockingQueue<Runnable>()
    );

    public Runtime(SmartSocietyApplicationContext context, Application application) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(application);
        this.context = context;
        this.application = application;
        this.flowsByType = ImmutableMap.copyOf(application.defineTaskFlowsByType());
    }

    boolean startTask(TaskDefinition definition) {
        TaskRequest request = application.createTaskRequest(definition);
        TaskFlowDefinition flowDefinition = flowsByType.get(request.getType());

        if (flowDefinition == null) {
            /* Exception or logging */
            return false;
        }

        CBTBuilder cbtBuilder = new CBTBuilder(context, flowDefinition, request);

        CollectiveBasedTask cbt = cbtBuilder.build();

        TaskRunner runner = application.getTaskRunner(cbt);
        executor.execute(runner);
        return true;
    }

    Optional<JsonNode> monitor(UUID taskId) {
        return
            Optional
                .ofNullable(runnerDescriptors.get(taskId))
            .map(d->d.getStateDescription());
    }

    void cancel(UUID taskId) {
        TaskRunnerDescriptor descriptor = runnerDescriptors.get(taskId);
        if ( descriptor != null ) {
            descriptor.cancel();
        }
    }

    private static class TaskRunnerDescriptor {
        private final long creationTimestamp = java.time.Instant.now().toEpochMilli();
        private final ExecutorService executor;
        private final TaskDefinition definition;
        private final CollectiveBasedTask cbt;
        private final TaskRunner runner;
        private final Function<Runnable, TaskResult> taskSubmitter;
        private Future<?> runnerFuture=null;

        public TaskRunnerDescriptor(ExecutorService executor, TaskDefinition definition, CollectiveBasedTask cbt, TaskRunner runner) {
            this.executor = executor;
            this.definition = definition;
            this.cbt = cbt;
            this.runner = runner;
            runnerFuture = executor.submit(runner);
            taskSubmitter = r -> {
                try {
                    /* TODO: CHECK HOW TO SYNCHRONIZE THE TWO RUNNERS */
                    executor.submit(runner).wait();
                    return cbt.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeWrapperException("Error on runner runs", e);
                }
            };
        }

        public void cancel() {
            runnerFuture.cancel(true);
            cbt.cancel(true);
        }


        public TaskDefinition getDefinition() {
            return definition;
        }

        public CollectiveBasedTask getCbt() {
            return cbt;
        }

        public TaskRunner getRunner() {
            return runner;
        }

        public long getCreationTimestamp() {
            return creationTimestamp;
        }

        public JsonNode getStateDescription() {
            ObjectNode node = jsonMapper.createObjectNode();
            node.set("applicationState", runner.getStateDescription());
            node.put("cbtState", cbt.getCurrentState().toString());
            return node;
        }
    }
}