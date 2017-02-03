package com.cloudbees.jenkins.support.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by stevenchristou on 2/3/17.
 */
@Data
public class BuildQueue implements Serializable {
    List<Item> buildQueue;
    int size;

    public void addItem(Item item) {
        buildQueue.add(item);
    }

    @Data
    public static class Item {
        String fullName;
        boolean isBlocked;
        String queueTime;
        String whyInQueue;
        List<Cause> causeList;
        List<TaskDispatcher> taskDispatcherList = new ArrayList<>();

        public void addCause(Cause cause) {
            causeList.add(cause);
        }

        public void addTaskDispatcher(TaskDispatcher td) {
            taskDispatcherList.add(td);
        }

        @Data
        public static class Cause {
            String description;
        }

        @Data
        public static class TaskDispatcher {
            String name;
            String canRun;
        }
    }
}
