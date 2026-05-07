package com.cinema.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportMessageEvent {

    private Long ticketId;
    private Long senderId;
    private String content;
    private Long recipientId;
}
