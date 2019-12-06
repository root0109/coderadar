package io.reflectoring.coderadar.projectadministration.filepattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.reflectoring.coderadar.projectadministration.domain.InclusionType;
import io.reflectoring.coderadar.projectadministration.port.driven.filepattern.ListFilePatternsOfProjectPort;
import io.reflectoring.coderadar.projectadministration.port.driver.filepattern.get.GetFilePatternResponse;
import io.reflectoring.coderadar.projectadministration.service.filepattern.ListFilePatternsOfProjectService;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListFilePatternsOfProjectServiceTest {

  @Mock private ListFilePatternsOfProjectPort listPatternsPortMock;

  private ListFilePatternsOfProjectService testSubject;

  @BeforeEach
  void setUp() {
    this.testSubject = new ListFilePatternsOfProjectService(listPatternsPortMock);
  }

  @Test
  void returnsTwoFilePatternsFromProject() {
    // given
    long projectId = 123L;

    GetFilePatternResponse expectedResponse1 =
        new GetFilePatternResponse(1L, "**/*.java", InclusionType.INCLUDE);
    GetFilePatternResponse expectedResponse2 =
        new GetFilePatternResponse(2L, "**/*.xml", InclusionType.EXCLUDE);

    when(listPatternsPortMock.listFilePatternResponses(projectId))
        .thenReturn(Arrays.asList(expectedResponse1, expectedResponse2));

    // when
    List<GetFilePatternResponse> actualResponse = testSubject.listFilePatterns(projectId);

    // then
    assertThat(actualResponse).containsExactly(expectedResponse1, expectedResponse2);
  }
}
