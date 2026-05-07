package com.cinema.support.config;

import com.cinema.support.entity.SupportMessage;
import com.cinema.support.entity.SupportTicket;
import com.cinema.support.entity.TicketStatus;
import com.cinema.support.repository.SupportMessageRepository;
import com.cinema.support.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final SupportTicketRepository supportTicketRepository;
    private final SupportMessageRepository supportMessageRepository;

    private static final Long DEMO_CLIENT_ID = 1L;
    private static final Long DEMO_ADMIN_ID = 2L;

    @Override
    public void run(String... args) {
        if (supportTicketRepository.count() == 0) {
            log.info("Loading initial support data...");

            LocalDateTime now = LocalDateTime.now();

            SupportTicket ticket = SupportTicket.builder()
                    .clientId(DEMO_CLIENT_ID)
                    .adminId(null)
                    .subject("Помогите с оформлением заказа")
                    .status(TicketStatus.OPEN)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            SupportTicket savedTicket = supportTicketRepository.save(ticket);

            SupportMessage clientMessage = SupportMessage.builder()
                    .ticketId(savedTicket.getId())
                    .senderId(DEMO_CLIENT_ID)
                    .content("Здравствуйте! Не могу оформить заказ на билет, при оплате выдаёт ошибку. Что делать?")
                    .sentAt(now)
                    .build();

            SupportMessage adminMessage = SupportMessage.builder()
                    .ticketId(savedTicket.getId())
                    .senderId(DEMO_ADMIN_ID)
                    .content("Здравствуйте! Попробуйте очистить кэш браузера и повторить попытку. Если проблема сохраняется, напишите нам ещё раз.")
                    .sentAt(now.plusMinutes(5))
                    .build();

            supportMessageRepository.save(clientMessage);
            supportMessageRepository.save(adminMessage);

            log.info("Demo support ticket #{} created with 2 messages", savedTicket.getId());
        }
    }
}
