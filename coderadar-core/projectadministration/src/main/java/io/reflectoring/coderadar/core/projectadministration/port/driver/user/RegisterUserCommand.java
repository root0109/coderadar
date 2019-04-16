package io.reflectoring.coderadar.core.projectadministration.port.driver.user;

import lombok.Value;

@Value
public class RegisterUserCommand {
    private Long id;
    private String username;
    private String password;
}
