package kr.ac.jbnu.se.tetris.support;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Maven에서도 기존 main 기반 검증을 독립 JVM과 -ea로 실제 실행하는 진입점 */
public final class HeadlessTestRunner {
    private HeadlessTestRunner() { }
    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        Path sources = root.resolve("src/test/java");
        List<String> suites = new ArrayList<String>();
        try (Stream<Path> paths = Files.walk(sources)) {
            paths.filter(path -> path.getFileName().toString().endsWith("Test.java"))
                    .forEach(path -> suites.add(sources.relativize(path).toString()
                            .replace(File.separatorChar, '.').replaceAll("\\.java$", "")));
        }
        Collections.sort(suites);
        if (suites.isEmpty()) throw new IllegalStateException("No headless test suites found");
        String dependencies = new String(Files.readAllBytes(root.resolve("target/runtime-classpath.txt")), StandardCharsets.UTF_8).trim();
        String cp = root.resolve("target/classes") + File.pathSeparator + root.resolve("target/test-classes")
                + File.pathSeparator + dependencies;
        String java = Paths.get(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
        for (String suite : suites) {
            Process process = new ProcessBuilder(java, "-Xmx2g", "-Djava.awt.headless=true", "-ea", "-cp", cp, suite)
                .directory(root.toFile()).inheritIO().start();
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly(); process.waitFor();
                throw new AssertionError("Test timed out: " + suite);
            }
            if (process.exitValue() != 0) throw new AssertionError("Test failed: " + suite + " (exit " + process.exitValue() + ")");
            System.out.println("PASS suite: " + suite);
        }
        System.out.println("PASS: " + suites.size() + " headless test suites");
    }
    private static boolean isWindows() { return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win"); }
}
