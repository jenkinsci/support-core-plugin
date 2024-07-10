package com.cloudbees.jenkins.support.slowrequest;

import static java.util.logging.Level.WARNING;

import com.cloudbees.jenkins.support.impl.ThreadDumps;
import com.cloudbees.jenkins.support.timer.FileListCap;
import hudson.Extension;
import jenkins.model.Jenkins;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

/**
 * Thread in charge of generating the set of thread dumps during a slowRequest scenario.
 *
 * @author Ignacio Roncero
 */
@Extension
public class SlowRequestThreadDumpsGenerator extends Thread{

    /**
     * When was the latest ThreadDumps generation in milliseconds
     */
    public static long latestGeneratedSlowRequestThreadDump = 0l;

    /**
     * How often (at minimum) we will capture the ThreadDump under a slowRequest scenario.
     * For example, if we set this value to 30 minutes, we will not generate any threadDumps the next 30 minutes
     * after the last generated threadDumps even though we are finding slowRequest during those 30 minutes.
     *
     * This value will help us avoid stressing the system regarding performance.
     */
    public static final long RECURRENCE_PERIOD_MIN =
            Integer.getInteger(SlowRequestThreadDumpsGenerator.class.getName() + ".RECURRENCE_PERIOD_MIN", 10);

    private static final long RECURRENCE_PERIOD_MILLIS = RECURRENCE_PERIOD_MIN * 60000;

    /**
     * The minimal number of SlowRequest found at the same time (in the last 3 seconds) to trigger the ThreadDump generation
     */
    public static final int MINIMAL_SLOW_REQUEST_COUNT =
            Integer.getInteger(SlowRequestThreadDumpsGenerator.class.getName() + ".MINIMAL_SLOW_REQUEST_COUNT", 5);

    /**
     * Number of ThreadDump that will be generated during the slowRequest scenario
     */
    public static final int TOTAL_ITERATIONS =
            Integer.getInteger(SlowRequestThreadDumpsGenerator.class.getName() + ".TOTAL_ITERATIONS", 4);

    /**
     * Time in seconds that we will wait between the ThreadDump generations (under the same slowRequest check)
     */
    public static final int FREQUENTLY_SEC =
            Integer.getInteger(SlowRequestThreadDumpsGenerator.class.getName() + ".FREQUENTLY_SEC", 5);

    /**
     * Limit the number of thread dumps to retain on slowRequest scenario
     */
    public static final int SLOW_REQUEST_THREAD_DUMPS_TO_RETAIN =
            Integer.getInteger(SlowRequestThreadDumpsGenerator.class.getName() + ".SLOW_REQUEST_THREAD_DUMPS_TO_RETAIN", 40);

    /**
     * Thread dumps generated on slowRequest scenario are stored in $JENKINS_HOME/slow-request-threaddumps
     */
    protected final FileListCap logs = new FileListCap(new File(Jenkins.get().getRootDir(), "slow-request-threaddumps"), SLOW_REQUEST_THREAD_DUMPS_TO_RETAIN);

    /**
     * Provide a means to disable the slow request checker. This is a volatile non-final field as if you run into
     * issues in a running Jenkins you may need to disable without restarting Jenkins.
     *
     * @since 2.12
     */
    public static volatile boolean DISABLED =
            Boolean.getBoolean(SlowRequestThreadDumpsGenerator.class.getName() + ".DISABLED");

    private final SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss.SSS");

    private long iota= 0l;


    public SlowRequestThreadDumpsGenerator(long iota){
        this.iota = iota;
    }

    public SlowRequestThreadDumpsGenerator() {}

    @Override
    public void run() {
        if(DISABLED) {
            return;
        }

        long fileNameDate = this.iota;

        for (int i = 0; i < TOTAL_ITERATIONS; i++){
            File threadDumpFile = logs.file(format.format(new Date(fileNameDate)) + ".txt");
            try (FileOutputStream fileOutputStream = new FileOutputStream(threadDumpFile)) {
                ThreadDumps.threadDump(fileOutputStream);
                logs.add(threadDumpFile);
            }catch(IOException ioe){
                LOGGER.log(WARNING,
                        "Support Core plugin can't generate automatically thread dumps under SlowRequest scenario",
                        ioe);
            }finally {
                fileNameDate+=FREQUENTLY_SEC * 1000;
            }
        }
    }

    public static synchronized boolean checkThreadDumpsTrigger(long iota) {
        boolean newThreadDumps = false;
        if(latestGeneratedSlowRequestThreadDump == 0l || iota - latestGeneratedSlowRequestThreadDump > RECURRENCE_PERIOD_MILLIS){
            newThreadDumps = true;
            latestGeneratedSlowRequestThreadDump = iota;
        } return newThreadDumps;
    }



    private static final Logger LOGGER = Logger.getLogger(SlowRequestThreadDumpsGenerator.class.getName());
}