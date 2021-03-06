package com.giggle.team.models;

import com.fasterxml.jackson.annotation.JsonView;
import com.giggle.team.utils.View;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

@Document(collection = "users")
@TypeAlias("user")
@NoArgsConstructor
@Setter
@Getter
public class UserEntity implements UserDetails {

    @Id
    private ObjectId id;
    @JsonView(View.Rest.class)
    private String email;
    @JsonView({View.Rest.class, View.Messages.class})
    private String username;
    @JsonView(View.Rest.class)
    private String password;
    private List<ObjectId> topics = new LinkedList<>();

    public UserEntity(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return null;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean equals(Object user) {
        if (!(user instanceof UserEntity)) {
            return false;
        } else return ((UserEntity) user).getId().equals(id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

}
