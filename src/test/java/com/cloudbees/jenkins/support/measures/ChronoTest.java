package com.cloudbees.jenkins.support.measures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.jenkins.support.util.Chrono;
import org.junit.jupiter.api.Test;

class ChronoTest {

    @Test
    void chronoTest() throws Exception {
        Chrono c = new Chrono("Test Chrono");
        Thread.sleep(1000);
        c.markFromPrevious("After 1s");
        assertTrue(c.getMeasure("After 1s") >= 1000);

        Thread.sleep(3000);
        c.mark("After 3s", "After 1s", "Test Chrono");
        assertTrue(c.getMeasure("After 3s") >= 3000);
        assertTrue(c.getMeasures("After 3s").get("Test Chrono") >= 4000);

        c.mark("After 3s from beginning");
        assertTrue(c.getMeasure("After 3s from beginning") >= 4000);

        Thread.sleep(400);
        c.markFromPrevious("After 400");
        assertTrue(c.getMeasure("After 400") >= 400);
        assertEquals(c.getMeasure("After 400"), c.getMeasures("After 400").get("After 3s from beginning"));

        System.out.println(c.printMeasures());
    }
}
