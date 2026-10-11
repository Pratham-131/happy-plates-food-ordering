package com.foodify.foodiesapi.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.foodify.foodiesapi.enums.UserRole;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "users")
@Builder
public class UserEntity {

    @Id
    private String id;
    private String name;
    private String email;
    private String password;
    @Builder.Default
    private UserRole role = UserRole.USER;
}
