package com.giggle.team.services;


import com.giggle.team.models.Topic;
import com.giggle.team.models.User;
import com.giggle.team.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Objects;

@Service
public class SubscriptionInterceptor implements ChannelInterceptor {

  private final UserRepository repository;

  public SubscriptionInterceptor(UserRepository repository) {
    this.repository = repository;
  }

  private static final Logger logger = LoggerFactory.getLogger(SubscriptionInterceptor.class);

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(message);
    if (Objects.equals(headerAccessor.getCommand(), StompCommand.SUBSCRIBE)) {
      Principal principal = headerAccessor.getUser();
      if (principal != null) {
        logger.info("Got new request for subscription to " + headerAccessor.getDestination() + " from " + principal.getName());
        if (!checkStompDestination(principal, headerAccessor.getDestination())) {
          throw new MessagingException("Requested destination is not available for this user");
        }
      } else {
        logger.info("Got new request for subscription to " + headerAccessor.getDestination() + " from not authenticated user");
        throw new MessagingException("Not authenticated");
      }
    }
    logger.info("Access to " + headerAccessor.getDestination() + " granted");
    return message;
  }

  private boolean checkStompDestination(Principal principal, String stompDestination) {
    User user = repository.findByUsername(principal.getName()).orElse(null);
    if (user != null && user.getTopics() != null) {
      for (Topic topic :
              user.getTopics()) {
        if (topic.getStompDestination().equals(stompDestination)) {
          return true;
        }
      }
    }
    return false;
  }
}