package com.ginogipsy.sanmartino.auth.api.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RegistrationRequest {

    private String username;
    private String email;
    private String password;
    private String confirmPassword;
    private String firstName;
    private String lastName;
}
