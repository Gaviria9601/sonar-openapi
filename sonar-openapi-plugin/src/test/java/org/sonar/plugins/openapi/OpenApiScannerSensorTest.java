/*
 * SonarQube OpenAPI Plugin
 * Copyright (C) 2018-2019 Societe Generale
 * vincent.girard-reydet AT socgen DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.openapi;

import com.google.common.collect.Iterables;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.sensor.error.AnalysisError;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.batch.sensor.issue.IssueLocation;
import org.sonar.api.issue.NoSonarFilter;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.FileLinesContext;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.log.LogTester;
import org.sonar.openapi.checks.CheckList;
import org.sonar.openapi.checks.ParsingErrorCheck;
import org.sonar.openapi.checks.PathMaskeradingCheck;
import org.sonar.openapi.metrics.OpenApiMetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OpenApiScannerSensorTest {
  private final Path baseDir = Paths.get("src/test/resources/sensor").toAbsolutePath();
  @org.junit.Rule
  public LogTester logTester = new LogTester();
  private SensorContextTester context;
  private ActiveRules activeRules;

  @Before
  public void init() {
    context = SensorContextTester.create(baseDir);
    context.settings().setProperty("sonar.openapi.path.v2", "v2");
    context.settings().setProperty("sonar.openapi.path.v3", "**");
  }

  @Test
  public void sensor_descriptor() {
    activeRules = (new ActiveRulesBuilder()).build();
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor();
    sensor().describe(descriptor);

    assertThat(descriptor.name()).isEqualTo("OpenAPI Scanner Sensor");
    assertThat(descriptor.languages()).containsOnly("openapi");
    assertThat(descriptor.type()).isEqualTo(InputFile.Type.MAIN);
  }

  @Test
  public void test_issues() {
    activeRules = (new ActiveRulesBuilder())
      .create(RuleKey.of(CheckList.REPOSITORY_KEY, "PathMaskerading"))
      .activate()
      .build();

    InputFile inputFile = inputFile("file1.yaml");
    sensor().execute(context);

    String key = "moduleKey:file1.yaml";
    assertThat(context.measure(key, CoreMetrics.NCLOC).value()).isEqualTo(29);
    assertThat(context.measure(key, OpenApiMetrics.PATHS_COUNT).value()).isEqualTo(2);
    assertThat(context.measure(key, OpenApiMetrics.OPERATIONS_COUNT).value()).isEqualTo(2);
    assertThat(context.measure(key, OpenApiMetrics.SCHEMAS_COUNT).value()).isEqualTo(2);
    assertThat(context.measure(key, CoreMetrics.COMPLEXITY).value()).isEqualTo(8);
    assertThat(context.measure(key, CoreMetrics.COMMENT_LINES).value()).isEqualTo(1);

    assertThat(context.allIssues()).hasSize(1);

    Issue issue = Iterables.get(context.allIssues(), 0);
    IssueLocation issueLocation = issue.primaryLocation();
    assertThat(issueLocation.inputComponent()).isEqualTo(inputFile);

    if (issue.ruleKey().rule().equals("PathMaskerading")) {
      assertThat(issueLocation.message()).isEqualTo(PathMaskeradingCheck.MASK_MESSAGE);
      assertThat(issueLocation.textRange()).isEqualTo(inputFile.newRange(6, 2, 6, 15));
      assertThat(issue.flows()).hasSize(1);
      assertThat(issue.gap()).isNull();
    }

    assertThat(context.allAnalysisErrors()).isEmpty();
  }

  @Test
  public void parse_error() {
    inputFile("parse-error.yaml");
    activeRules = (new ActiveRulesBuilder())
      .create(RuleKey.of(CheckList.REPOSITORY_KEY, ParsingErrorCheck.CHECK_KEY))
      .activate()
      .build();
    sensor().execute(context);
    assertThat(context.allIssues()).hasSize(2);
    assertThat(context.allAnalysisErrors())
        .extracting(e -> e.inputFile().filename(), e -> e.location().line(), e -> e.location().lineOffset(), AnalysisError::message)
        .containsExactlyInAnyOrder(
            tuple("parse-error.yaml", 3, 2, "Missing required properties: [version]"),
            tuple("parse-error.yaml", 7, 6, "Missing required properties: [responses]")
        );
  }

  @Test
  public void cancelled_analysis() {
    InputFile inputFile = inputFile("file1.yaml");
    activeRules = (new ActiveRulesBuilder()).build();
    context.setCancelled(true);
    sensor().execute(context);
    assertThat(context.allAnalysisErrors()).isEmpty();
  }

  private OpenApiScannerSensor sensor() {
    CheckFactory checkFactory = new CheckFactory(activeRules);
    FileLinesContextFactory fileLinesContextFactory = mock(FileLinesContextFactory.class);
    FileLinesContext fileLinesContext = mock(FileLinesContext.class);
    when(fileLinesContextFactory.createFor(Mockito.any(InputFile.class))).thenReturn(fileLinesContext);
    return new OpenApiScannerSensor(checkFactory, fileLinesContextFactory, new NoSonarFilter());
  }

  private InputFile inputFile(String name) {
    DefaultInputFile inputFile = TestInputFileBuilder.create("moduleKey", name)
      .setModuleBaseDir(baseDir)
      .setCharset(StandardCharsets.UTF_8)
      .setType(InputFile.Type.MAIN)
      .setLanguage(OpenApi.KEY)
      .initMetadata(TestUtils.fileContent(new File(baseDir.toFile(), name), StandardCharsets.UTF_8))
      .build();
    context.fileSystem().add(inputFile);
    return inputFile;
  }
}
