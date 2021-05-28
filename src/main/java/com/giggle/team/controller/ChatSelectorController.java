package com.giggle.team.controller;

import com.giggle.team.models.Topic;
import com.giggle.team.models.UserEntity;
import com.giggle.team.repositories.TopicRepository;
import com.giggle.team.repositories.UserRepository;
import com.giggle.team.utils.MessageUtils;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
public class ChatSelectorController {
  private final UserRepository userRepository;
  private final TopicRepository topicRepository;
  private final MessageUtils messageUtils;

  public ChatSelectorController(UserRepository userRepository, TopicRepository topicRepository, MessageUtils messageUtils) {
    this.userRepository = userRepository;
    this.topicRepository = topicRepository;
    this.messageUtils = messageUtils;
  }

  @RequestMapping(value = "/find/user", method = RequestMethod.GET, produces = "application/json")
  @ResponseBody
  public List<Map<String, String>> findUsers(@RequestParam("query") String query){
    List<UserEntity> queryResult = userRepository.findByUsernameIgnoreCaseStartsWith(query);
    List<Map<String, String>> toSend = new ArrayList<>();
    for (UserEntity entity:
         queryResult) {
      Map<String, String> userData = new HashMap<>();
      userData.put("username", entity.getUsername());
      userData.put("email", entity.getEmail());
      toSend.add(userData);
    }
    return toSend;
  }

  @RequestMapping(value = "/show/topic", method = RequestMethod.GET, produces = "application/json")
  @ResponseBody
  public Map<String, List<String>> showTopics(Principal principal){
    List<Topic> queryResult = topicRepository.findAll();
    Map<String, List<String>> toSend = new HashMap<>();
    for (Topic topic:
            queryResult) {
      if(messageUtils.checkDestination(principal, topic.getStompDestination())){
        List<String> usernames = new LinkedList<>();
        for (UserEntity user:
             topic.getUsers()) {
          usernames.add(user.getUsername());
        }
        toSend.put(topic.getStompDestination(), usernames);
      }
    }
    return toSend;
  }
}
