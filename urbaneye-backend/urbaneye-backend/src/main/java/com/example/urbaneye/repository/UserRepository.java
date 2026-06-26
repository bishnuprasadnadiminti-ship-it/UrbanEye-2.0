package com.example.urbaneye.repository;

import com.example.urbaneye.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

interface JpaUserRepository extends JpaRepository<User, String> {}

@Repository
public class UserRepository {
    private final JpaUserRepository jpaUserRepository;

    public UserRepository(JpaUserRepository jpaUserRepository) {
        this.jpaUserRepository = jpaUserRepository;
    }

    @Transactional(readOnly = true)
    public User findById(String id) {
        return jpaUserRepository.findById(id).orElse(null);
    }

    public void save(User user) {
        jpaUserRepository.save(user);
    }
}
