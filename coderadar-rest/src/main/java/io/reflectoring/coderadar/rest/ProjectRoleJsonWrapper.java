package io.reflectoring.coderadar.rest;

import io.reflectoring.coderadar.useradministration.domain.ProjectRole;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectRoleJsonWrapper {
  @NotNull private ProjectRole role;
}
