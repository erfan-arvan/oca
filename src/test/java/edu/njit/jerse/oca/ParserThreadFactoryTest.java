package edu.njit.jerse.oca;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import edu.njit.jerse.oca.model.ProgramElementId;
import edu.njit.jerse.oca.model.ProgramPoint;
import edu.njit.jerse.oca.model.ProgramPointImpl;
import edu.njit.jerse.oca.model.ProgramPointKind;
import edu.njit.jerse.oca.parse.MethodSignatureUtil;
import edu.njit.jerse.oca.parse.context.ContextUtils;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/**
 * Checks that threads from {@link App#parserThreadFactory} parse and resolve Java code the same way
 * as the main thread, since Oca builds prompts and injects guards on worker threads.
 */
public class ParserThreadFactoryTest {

  private static final Path SRC_ROOT = Path.of("src/test/resources/testproj");

  private static ProgramPoint totalNameLengthEntry() throws Exception {
    MethodDeclaration m =
        StaticJavaParser.parse(SRC_ROOT.resolve("foo/Bar.java"))
            .findFirst(MethodDeclaration.class, d -> d.getNameAsString().equals("totalNameLength"))
            .orElseThrow();
    ProgramElementId id =
        ProgramElementId.forMethod(
            "foo", "Bar", "", "foo/Bar.java", MethodSignatureUtil.jvmDescriptorBestEffort(m));
    return new ProgramPointImpl(id, ProgramPointKind.METHOD_ENTRY);
  }

  @Test
  void typeDocumentationIsExtractedOnWorkerThreads() throws Exception {
    ProgramPoint point = totalNameLengthEntry();
    ExecutorService pool = Executors.newFixedThreadPool(2, App.parserThreadFactory(SRC_ROOT));
    try {
      Optional<String> typeDoc =
          pool.submit(() -> ContextUtils.extractTypeDocumentation(point, SRC_ROOT)).get();
      assertTrue(typeDoc.isPresent(), "no type documentation on a worker thread");
      assertTrue(typeDoc.get().contains("User"), "type documentation does not describe User");
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void workerThreadsParseRecentJavaSyntax() throws Exception {
    String source =
        "class A {\n"
            + "  record P(int x) {}\n"
            + "  int f(Object o) {\n"
            + "    if (o instanceof String s) return s.length();\n"
            + "    return switch (o.hashCode()) { case 1 -> 2; default -> 3; };\n"
            + "  }\n"
            + "}\n";
    ExecutorService pool = Executors.newFixedThreadPool(1, App.parserThreadFactory(SRC_ROOT));
    try {
      pool.submit(() -> assertDoesNotThrow(() -> StaticJavaParser.parse(source))).get();
    } finally {
      pool.shutdownNow();
    }
  }
}
