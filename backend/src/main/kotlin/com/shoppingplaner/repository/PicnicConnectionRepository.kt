package com.shoppingplaner.repository

import com.shoppingplaner.model.PicnicConnection
import org.springframework.data.jpa.repository.JpaRepository

interface PicnicConnectionRepository : JpaRepository<PicnicConnection, String>
