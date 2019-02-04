package com.cloudbees.jenkins.support.measures;

import com.cloudbees.jenkins.support.util.Chrono;
import org.junit.Test;

public class ChronoTest {
    @Test
    public void chronoTest() throws Exception {
        Chrono c = new Chrono("Test Chrono");
        halt(1000);
        c.mark("After 1s", "");
        halt(3000);
        c.mark("After 3s", "After 1s", "Test Chrono");
        c.mark("After 3s from beginning");

        halt(400);
        c.mark("After 400");
        halt (500);
        c.mark("After 500");
        System.out.println(c.printMeasures());
    }

    private void halt(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}
