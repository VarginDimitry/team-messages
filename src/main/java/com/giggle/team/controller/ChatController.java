package com.giggle.team.controller;

import com.giggle.team.listener.UserListenerContainer;
import com.giggle.team.models.Message;
import com.giggle.team.models.Topic;
import com.giggle.team.models.UserEntity;
import com.giggle.team.repositories.TopicRepository;
import com.giggle.team.repositories.UserRepository;
import com.giggle.team.services.KafkaProducer;
import com.giggle.team.utils.MessageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping(value = "/kafka/chat")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final Map<String, ArrayList<UserListenerContainer>> listenersMap;

    private final ConcurrentKafkaListenerContainerFactory<String, String> factory;
    private final MessageUtils messageUtils;
    private final SimpMessagingTemplate template;
    private final KafkaProducer producer;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final KafkaProducer kafkaProducer;

    public ChatController(ConcurrentKafkaListenerContainerFactory<String, String> factory,
                          Map<String, ArrayList<UserListenerContainer>> listenersMap,
                          SimpMessagingTemplate template,
                          KafkaProducer producer,
                          MessageUtils messageUtils, TopicRepository topicRepository, UserRepository userRepository, KafkaProducer kafkaProducer) {
        this.factory = factory;
        this.listenersMap = listenersMap;
        this.template = template;
        this.producer = producer;
        this.messageUtils = messageUtils;
        this.topicRepository = topicRepository;
        this.userRepository = userRepository;
        this.kafkaProducer = kafkaProducer;
    }

    /**
     * Receiving message from Web Browser using STOMP CLIENT and further Sending
     * message to a KAFKA TOPIC
     */
    @MessageMapping("/sendMessage")
    @RequestMapping(value = "/sendMessage", method = RequestMethod.GET, produces = "application/json")
    public void sendMessage(Principal principal, @Payload Message message) {
        if (principal != null && messageUtils.checkDestination(principal, message.getChatId())) {
            logger.info("Got new message from " + principal.getName() + " to " + message.getChatId());
            producer.send(message.getChatId(), message.getChatId() + "-"
                    + Message.MessageType.valueOf(message.getType().name()) + "-" + message.getContent() + "-"
                    + message.getSender() + "-" + userRepository.findByEmail(message.getSender()).getUsername() + "-"
                    + UUID.randomUUID().toString().replace("-", ""));
            logger.info("Message to " + message.getChatId() + " from " + principal.getName() + " was sent");
        } else {
            logger.error("Message to " + message.getChatId() + " was not sent");
        }
    }

    /**
     * Adding username in Websocket
     */
    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public Message addUser(@Payload Message message, SimpMessageHeaderAccessor headerAccessor) {
        logger.debug("added username in web socket session");
        // Add username in web socket session

        // fixme
        assert (headerAccessor != null);
        assert (headerAccessor.getSessionAttributes() != null);

        if (message == null || message.getSender() == null || message.getSender().isEmpty()) {
            headerAccessor.getSessionAttributes().put("username", "unknown");
        } else {
            headerAccessor.getSessionAttributes().put("username", message.getSender());
        }
        return message;
    }

    /**
     * Joining to a specific chat:
     * Creating kafka listener container for each user for each chat and putting it to map
     * Each listener will send received message to specific STOMP topic
     */
    @MessageMapping("/chat.join")
    public void joinChat(Principal principal, @Payload Message message, @Header("simpSessionId") String sessionId) {
        if (messageUtils.checkDestination(principal, message.getChatId())) {
            logger.info("Received request for listener creation from " + sessionId);
            if (!listenersMap.containsKey(sessionId)) {
                logger.info("User's list of listeners not exist, creating new one");
                listenersMap.put(sessionId, new ArrayList<>());
                logger.info("Creating listener for " + sessionId);
                listenersMap.get(sessionId).add(
                        new UserListenerContainer(message.getChatId(), principal.getName(), message.getChatId(), factory, template, sessionId)
                );
            } else {
                if (!listenersMap.get(sessionId).contains(
                        new UserListenerContainer(principal.getName(), message.getChatId()))) {
                    logger.info("No already existing listener, creating new one");
                    logger.info("Creating listener for " + sessionId);
                    listenersMap.get(sessionId).add(
                            new UserListenerContainer(message.getChatId(), principal.getName(), message.getChatId(), factory, template, sessionId)
                    );
                } else {
                    logger.info("Such listener already exists");
                }
            }
        } else {
            logger.info("Received request for listener creation but access denied or principal is null");
        }
    }

    @RequestMapping(value = "/createChat", method = RequestMethod.POST, consumes = "application/json")
    @ResponseBody
    public ResponseEntity<String> createChat(Principal principal, @RequestBody LinkedList<String> emailsToChat) {
        if (emailsToChat == null || emailsToChat.isEmpty()) {
            return new ResponseEntity<>("Can`t create chat with only one user", HttpStatus.BAD_REQUEST);
        }
        List<UserEntity> usersToAdd = new LinkedList<>();
        usersToAdd.add(userRepository.findByEmail(principal.getName()));
        for (String email : emailsToChat) {
            UserEntity user = userRepository.findByEmail(email);
            if (Objects.isNull(user)) {
                return new ResponseEntity<>("Can`t find one or more users", HttpStatus.NOT_FOUND);
            }
            if (user.getEmail().equals(usersToAdd.get(0).getEmail())){
                return new ResponseEntity<>("Can`t create chat between same user", HttpStatus.BAD_REQUEST);
            }
            usersToAdd.add(user);
        }
        String chatName = UUID.randomUUID().toString().replace("-", "");
        boolean topicExists = false;
        List<Topic> topics = topicRepository.findAllById(usersToAdd.get(0).getTopics());
        for (Topic topic : topics) {
            if (!topic.getStompDestination().equals("main")) {
                if (topic.getUsers().size() == usersToAdd.size() && topic.getUsers().containsAll(usersToAdd)) {
                    topicExists = true;
                    break;
                }
            }
        }
        if (!topicExists) {
            Topic toCreate = new Topic(chatName, chatName);
            for (UserEntity user : usersToAdd) {
                toCreate.addUser(user);
            }
            topicRepository.save(toCreate);
            for (UserEntity user : usersToAdd) {
                user.getTopics().add(toCreate.getId());
                userRepository.save(user);
                template.convertAndSendToUser(user.getEmail(), "/queue/service",
                        new Message("service", Message.MessageType.SYSTEM,
                                "CHATS_UPDATE", "system", "system", "system"));
            }
            kafkaProducer.send(chatName, chatName + "-" + "SYSTEM"
                    + "-" + "NEW CHAT CREATED" + "-System-System-" +
                    UUID.randomUUID().toString().replace("-", ""));
            return new ResponseEntity<>("New chat created", HttpStatus.OK);
        }
        return new ResponseEntity<>("Same chat already exists", HttpStatus.CONFLICT);
    }

    @RequestMapping(value = "/removeChat", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public ResponseEntity<String> removeChat(Principal principal, @RequestParam("chatId") String chatId) {
        if (messageUtils.checkDestination(principal, chatId) && !chatId.equals("main")) {
            Topic topic = topicRepository.findByStompDestination(chatId);
            List<UserEntity> users = topic.getUsers();
            for (UserEntity user :
                    users) {
                user.getTopics().remove(topic.getId());
                userRepository.save(user);
            }
            topicRepository.removeTopicById(topic.getId());
            return new ResponseEntity<>("Chat removed", HttpStatus.OK);
        }
        return new ResponseEntity<>("User not allowed to manipulate this chat", HttpStatus.FORBIDDEN);
    }

    @RequestMapping(value = "/addUsers", method = RequestMethod.POST, consumes = "application/json")
    @ResponseBody
    public ResponseEntity<String> addUsers(Principal principal, @RequestParam("chatId") String chatId, @RequestBody LinkedList<String> emailsToChat) {
        if (messageUtils.checkDestination(principal, chatId) && !chatId.equals("main")) {
            Topic topic = topicRepository.findByStompDestination(chatId);
            for (String email :
                    emailsToChat) {
                UserEntity user = userRepository.findByEmail(email);
                if (!user.getTopics().contains(topic.getId())) {
                    topic.getUsers().add(user);
                    user.getTopics().add(topic.getId());
                    userRepository.save(user);
                }
            }
            topicRepository.save(topic);
            return new ResponseEntity<>("Users added to chat", HttpStatus.OK);
        }
        return new ResponseEntity<>("User not allowed to manipulate this chat", HttpStatus.FORBIDDEN);
    }

    public Topic initMainChat(){
        Topic main = new Topic("main", "main");
        kafkaProducer.send("main", "main-SYSTEM-Main chat initialized-System-System-" +
                UUID.randomUUID().toString().replace("-", ""));
        topicRepository.save(main);
        return main;
    }

}



