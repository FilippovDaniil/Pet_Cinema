package com.cinema.support.repository;

import com.cinema.support.entity.SupportTicket;
import com.cinema.support.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findByClientId(Long clientId);

    List<SupportTicket> findByAdminId(Long adminId);

    List<SupportTicket> findByStatus(TicketStatus status);
}
