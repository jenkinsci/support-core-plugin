package com.cloudbees.jenkins.support.model;

import lombok.Data;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by stevenchristou on 2/3/17.
 */
@Data
public class BuildQueue implements Serializable, MarkdownFile {
    List<Item> buildQueue = new ArrayList<>();
    int size;
    boolean isQuietingDown;

    public void addItem(Item item) {
        buildQueue.add(item);
    }

    @Data
    public static class Item {
        String fullName;
        boolean isBlocked;
        String queueTime;
        String whyInQueue;
        List<Cause> causeList = new ArrayList<>();
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

    @Override
    public void toMarkdown(PrintWriter out) {
        out.println("Current build queue has " +  size + " item(s).");
        out.println("---------------");

        for (Item item : buildQueue) {
                out.println(" * Name of item: " + item.getFullName());

            out.println("    - In queue for: " + item.getQueueTime());
            out.println("    - Is blocked: " + item.isBlocked());
            out.println("    - Why in queue: " + item.getWhyInQueue());

            for (Item.Cause cause : item.getCauseList()) {
                out.println("    - Current queue trigger cause: " + cause.getDescription());
            }

            for (Item.TaskDispatcher taskDispatcher : item.getTaskDispatcherList()) {
                out.println("  * Task Dispatcher: " + taskDispatcher);
                out.println("    - Can run: " + taskDispatcher.getCanRun());
            }
            out.println("----");
            out.println();
        }

        out.println("Is quieting down: " + isQuietingDown());
    }
}
