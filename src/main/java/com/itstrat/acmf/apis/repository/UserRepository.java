package com.itstrat.acmf.apis.repository;

import com.itstrat.acmf.apis.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

	//	@Query(value = "SELECT u.email from User u where u.email =:email")
	User findByEmail(String email);

}
