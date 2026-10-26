package com.hirehub.mapper;

import com.hirehub.dto.auth.UserSummaryResponse;
import com.hirehub.entity.User;
import org.springframework.stereotype.Component;

/**
 * A plain hand-written mapper, not MapStruct or ModelMapper. WHY: the project has a small,
 * fixed set of entity-to-DTO conversions, and a hand-written method is one line, has no
 * generated code to inspect when something looks wrong, and needs no annotation processor
 * added to the build. MapStruct earns its place on a larger project with dozens of mappings
 * and nested object graphs — not yet here.
 */
@Component
public class UserMapper {

    public UserSummaryResponse toSummary(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getName()
        );
    }
}
