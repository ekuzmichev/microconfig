package io.microconfig;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.LinkedHashSet;

import static io.microconfig.MicroconfigParams.parse;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;

class MicroconfigParamsTest {
    MicroconfigParams params = parse(
            "-r", "configs/repo",
            "-d", "destination",
            "-e", "dev",
            "-envs", "dev, test",
            "-g", "g1,g2",
            "-s", "s1, s2",
            "-output", "json"
    );
    MicroconfigParams empty = MicroconfigParams.parse();
    MicroconfigParams singleEnvBuild = parse("-e", "dev");

    @Test
    void rootDir() {
        assertEquals(new File("configs/repo"), params.rootDir());
    }

    @Test
    void destinationDir() {
        assertEquals("destination", params.destinationDir());
        assertEquals("build", empty.destinationDir());
    }

    @Test
    void env() {
        assertEquals("dev", params.environments().iterator().next());
    }

    @Test
    void envs() {
        assertEquals(new LinkedHashSet<>(asList("dev", "test")), params.environments());
    }

    @Test
    void groups() {
        assertEquals(asList("g1", "g2"), params.groups());
        assertEquals(emptyList(), empty.groups());
    }

    @Test
    void services() {
        assertEquals(asList("s1", "s2"), params.services());
        assertEquals(emptyList(), empty.services());
    }

    @Test
    void jsonOutput() {
        assertFalse(empty.jsonOutput());
        assertTrue(params.jsonOutput());
    }

    @Test
    void testVersion() {
        assertTrue(parse("-v").version());
    }

    @Test
    void isSingleEnvBuild() {
        assertFalse(params.isSingleEnvBuild());
        assertTrue(singleEnvBuild.isSingleEnvBuild());
    }
}