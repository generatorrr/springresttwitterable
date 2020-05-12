package com.example.springresttwitterable.entity.dto.user;

import com.example.springresttwitterable.entity.enums.Role;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.hibernate.validator.constraints.NotEmpty;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Set;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserForAdminDTO implements Serializable
{
    
    private String id;

    @NotNull
    @NotEmpty
    private String name;

    @NotNull
    @NotEmpty
    private String email;

    private String userpic;

    private String gender;

    private Set<Role> roles;

    private String locale;
    
    private LocalDateTime lastVisit;
}
