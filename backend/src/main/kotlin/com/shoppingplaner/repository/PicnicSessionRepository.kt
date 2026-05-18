package com.shoppingplaner.repository

import com.shoppingplaner.model.PicnicSession
import org.springframework.data.jpa.repository.JpaRepository

interface PicnicSessionRepository : JpaRepository<PicnicSession, Long> {
    fun findTopByOrderByUpdatedAtDesc(): PicnicSession?
}
