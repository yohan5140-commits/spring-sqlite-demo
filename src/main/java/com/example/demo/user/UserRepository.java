package com.example.demo.user;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.ListPagingAndSortingRepository;

public interface UserRepository extends ListCrudRepository<User, Long>, ListPagingAndSortingRepository<User, Long> {
}
