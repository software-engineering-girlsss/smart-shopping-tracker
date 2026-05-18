package com.shoppingplaner.repository

import com.shoppingplaner.model.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, String>
