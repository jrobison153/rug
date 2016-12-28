package com.atomist.rug.kind.docker

import com.atomist.rug.DefaultRugPipeline
import com.atomist.rug.InterpreterRugPipeline.DefaultRugArchive
import com.atomist.rug.test.{ParserCombinatorTestScriptParser, RugTestRunnerTestSupport}
import com.atomist.source.{SimpleFileBasedArtifactSource, StringFileArtifact}
import org.scalatest.{FlatSpec, Matchers}

class DockerFileTypeTestRunnerTest extends FlatSpec with Matchers with RugTestRunnerTestSupport {

  it should "test the test that tests that the editor updates Dockerfile" in {
    val prog =
      """
        |editor UpdateServicePort
        |
        |param servicePort: ^.*$
        |
        |with DockerFile d when path = "src/main/docker/Dockerfile"
        |  do addOrUpdateExpose servicePort
        |
      """.stripMargin

    val scenario =
      """
        |scenario UpdateServicePort should update Dockerfile
        |
        |let servicePort = 8181
        |
        |given
        |	ArchiveRoot
        |
        |	UpdateServicePort
        |
        |then
        |  fileExists "src/main/docker/Dockerfile"
        |	 and fileContains "src/main/docker/Dockerfile" "EXPOSE 8181"
      """.stripMargin

    val rp =new DefaultRugPipeline()
    val as = new SimpleFileBasedArtifactSource(DefaultRugArchive, StringFileArtifact(rp.defaultFilenameFor(prog), prog))
    val eds = rp.create(as,  None)
    val dockerfile = StringFileArtifact("src/main/docker/Dockerfile",
      """
        |FROM java:8-jre
        |
        |COPY @project.build.finalName@.jar /opt/build/
        |
        |WORKDIR /opt/build
        |
        |EXPOSE 8080
        |
        |CMD ["java", "-Xmx1g", "-jar", "@project.build.finalName@.jar"]
        |
      """.stripMargin)

    val test = ParserCombinatorTestScriptParser.parse(StringFileArtifact("x.rt", scenario))
    val executedTests = testRunner.run(test, new SimpleFileBasedArtifactSource("", dockerfile), eds)
    executedTests.tests.size should be(1)
    executedTests.tests.head match {
      case t if t.passed =>
        // Ok
    }
  }
}
