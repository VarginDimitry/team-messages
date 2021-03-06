package com.giggle.team.repositories;

import com.giggle.team.models.UserEntity;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends MongoRepository<UserEntity, ObjectId> {
    UserEntity findByEmail(String email);

    List<UserEntity> findByUsernameIgnoreCaseStartsWith(String query);

    List<UserEntity> findAll();
}
