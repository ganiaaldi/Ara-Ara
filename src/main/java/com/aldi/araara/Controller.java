package com.aldi.araara;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.client.LineSignatureValidator;
import com.linecorp.bot.client.MessageContentResponse;
import com.linecorp.bot.model.Multicast;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.*;
import com.linecorp.bot.model.event.source.GroupSource;
import com.linecorp.bot.model.event.source.RoomSource;
import com.linecorp.bot.model.message.FlexMessage;
import com.linecorp.bot.model.message.StickerMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.flex.container.FlexContainer;
import com.linecorp.bot.model.objectmapper.ModelObjectMapper;
import com.linecorp.bot.model.profile.UserProfileResponse;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static jdk.nashorn.internal.objects.NativeArray.push;

@RestController
public class Controller {

    @Autowired
    @Qualifier("lineMessagingClient")
    private LineMessagingClient lineMessagingClient;

    @Autowired
    @Qualifier("lineSignatureValidator")
    private LineSignatureValidator lineSignatureValidator;

    @RequestMapping(value="/webhook", method = RequestMethod.POST)
    public ResponseEntity<String> callback(
            @RequestHeader("X-Line-Signature") String xLineSignature,
            @RequestBody String eventsPayload)
    {
        try {
            if (!lineSignatureValidator.validateSignature(eventsPayload.getBytes(), xLineSignature)) {
                throw new RuntimeException("Invalid Signature Validation");
            }

            // parsing event
            ObjectMapper objectMapper = ModelObjectMapper.createNewObjectMapper();
            EventsModel eventsModel = objectMapper.readValue(eventsPayload, EventsModel.class);
            eventsModel.getEvents().forEach((event)->{
                if (event instanceof MessageEvent) {
                    if (event.getSource() instanceof GroupSource || event.getSource() instanceof RoomSource) {
                        handleGroupRoomChats((MessageEvent) event);
                    } else {
                        handleOneOnOneChats((MessageEvent) event);
                    }
                }
            });

            return new ResponseEntity<>(HttpStatus.OK);
        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(value="/pushmessage/{id}/{message}", method = RequestMethod.GET)
    public ResponseEntity<String> pushmessage(
            @PathVariable("id") String userId,
            @PathVariable("message") String textMsg
    ){
        TextMessage textMessage = new TextMessage(textMsg);
        PushMessage pushMessage = new PushMessage(userId,textMessage);
        push(pushMessage);

        return new ResponseEntity<String>("Push message : " +textMsg+"\n terkirim ke :" +userId,
        HttpStatus.OK);
    }

    private void push(PushMessage pushMessage){
        try{
            lineMessagingClient.pushMessage(pushMessage).get();
        } catch (InterruptedException | ExecutionException e){
            throw  new RuntimeException(e);
        }
    }

    @RequestMapping(value="/multicast", method=RequestMethod.GET)
    public ResponseEntity<String> multicast(){
        String[] userIdList = {
                "U79eca2870030547c1a0d3a9e7ec0d973",
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"};
        Set<String> listUsers = new HashSet<String>(Arrays.asList(userIdList));
        if(listUsers.size() > 0){
            String textMsg = "Halo semua, apa kabar hari ini? ";
            sendMulticast(listUsers, textMsg);
        }
        return new ResponseEntity<String>(HttpStatus.OK);
    }

    private void sendMulticast(Set<String> sourceUsers, String txtMessage){
        TextMessage message = new TextMessage(txtMessage);
        Multicast multicast = new Multicast(sourceUsers, message);

        try {
            lineMessagingClient.multicast(multicast).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @RequestMapping(value = "/profile/{id}", method = RequestMethod.GET)
    public ResponseEntity<String> profile(){
        String userId = "Isi dengan userId Anda";
        UserProfileResponse profile = getProfile(userId);

        if (profile != null) {
            String profileName = profile.getDisplayName();
            TextMessage textMessage = new TextMessage("Ara-ara, Halooo " + profileName);
            PushMessage pushMessage = new PushMessage(userId, textMessage);
            push(pushMessage);

            return new ResponseEntity<String>("Ara-ara, Halooo "+profileName, HttpStatus.OK);
        }

        return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
    }

    private UserProfileResponse getProfile(String userId){
        try {
            return lineMessagingClient.getProfile(userId).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @RequestMapping(value = "/content/{id}", method = RequestMethod.GET)
    public ResponseEntity content(
            @PathVariable("id") String messageId
    ){
        MessageContentResponse messageContent = getContent(messageId);

        if(messageContent != null) {
            HttpHeaders headers = new HttpHeaders();
            String[] mimeType = messageContent.getMimeType().split("/");
            headers.setContentType(new MediaType(mimeType[0], mimeType[1]));

            InputStream inputStream = messageContent.getStream();
            InputStreamResource inputStreamResource = new InputStreamResource(inputStream);

            return new ResponseEntity<>(inputStreamResource, headers, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    private MessageContentResponse getContent(String messageId) {
        try {
            return lineMessagingClient.getMessageContent(messageId).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleOneOnOneChats(MessageEvent event) {
            if (event.getMessage() instanceof AudioMessageContent
                    || event.getMessage() instanceof ImageMessageContent
                    || event.getMessage() instanceof VideoMessageContent
                    || event.getMessage() instanceof FileMessageContent
            ) {
                handleContentMessage(event);
            } else if (event.getMessage() instanceof TextMessageContent) {
                handleTextMessage(event);
            } else {
                replyText(event.getReplyToken(), "Stikernya bagus kak! (/^^)/");
            }
    }

    private void handleContentMessage(MessageEvent event) {
        String baseURL     = "https://araara.herokuapp.com";
        String contentURL  = baseURL+"/content/"+ event.getMessage().getId();
        String contentType = event.getMessage().getClass().getSimpleName();
        String textMsg     = contentType.substring(0, contentType.length() -14)
                + "nya bagus kak! (/^^)/ \n bisa juga diakses dari link ini ( *｀ω´) :\n "
                + contentURL;
        replyText(event.getReplyToken(), textMsg);
    }

    private void handleTextMessage(MessageEvent event) {
        TextMessageContent textMessageContent = (TextMessageContent) event.getMessage();

        if (textMessageContent.getText().toLowerCase().contains("ara")) {
            replyFlexMessage(event.getReplyToken());
        }
            else if (textMessageContent.getText().toLowerCase().contains("satu") ||
                    textMessageContent.getText().toLowerCase().contains("dua") ||
                    textMessageContent.getText().toLowerCase().contains("tiga")) {

            }
         else {
           // replyText(event.getReplyToken(), textMessageContent.getText());
            replyText(event.getReplyToken(), "Coba kakak ketikan perintah 'ara'");
        }
    }

    private void handleGroupRoomChats(MessageEvent event) {
        if (!event.getSource().getUserId().isEmpty()) {
            String userId = event.getSource().getUserId();
            UserProfileResponse profile = getProfile(userId);
            replyText(event.getReplyToken(), "Ara-ara, Halooo " + profile.getDisplayName());
        } else {
            replyText(event.getReplyToken(), "Ara-ara, Halooo");
        }
    }

        private void reply(ReplyMessage replyMessage) {
            try {
                lineMessagingClient.replyMessage(replyMessage).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }


        private void replyText(String replyToken, String messageToUser){
            TextMessage textMessage = new TextMessage(messageToUser);
            ReplyMessage replyMessage = new ReplyMessage(replyToken, textMessage);
            reply(replyMessage);
        }


        private void replySticker(String replyToken, String packageId, String stickerId){
            StickerMessage stickerMessage = new StickerMessage(packageId, stickerId);
            ReplyMessage replyMessage = new ReplyMessage(replyToken, stickerMessage);
            reply(replyMessage);
        }


    private void replyFlexMessage(String replyToken) {
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            String flexTemplate = IOUtils.toString(classLoader.getResourceAsStream("flex_messages.json"));

            ObjectMapper objectMapper = ModelObjectMapper.createNewObjectMapper();
            FlexContainer flexContainer = objectMapper.readValue(flexTemplate, FlexContainer.class);

            ReplyMessage replyMessage = new ReplyMessage(replyToken, new FlexMessage("Ara Ara", flexContainer));
            reply(replyMessage);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
