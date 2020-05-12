package com.example.springresttwitterable.controller.MessageControllerTest;

import com.example.springresttwitterable.TestConstants;
import com.example.springresttwitterable.controller.BaseControllerTest;
import com.example.springresttwitterable.controller.MessageController;
import com.example.springresttwitterable.entity.User;
import com.example.springresttwitterable.entity.dto.message.ListMessageDTO;
import com.example.springresttwitterable.entity.dto.message.MessageDTO;
import com.example.springresttwitterable.entity.mapper.UserMapper;
import com.example.springresttwitterable.error.RestExceptionHandler;
import com.example.springresttwitterable.repository.MessageRepository;
import com.example.springresttwitterable.repository.UserRepository;
import com.example.springresttwitterable.utils.TestDataHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test class for {@link MessageController}'s "updateMessage" method.
 * <p>
 * Created on 11/5/18.
 * <p>
 * @author Vlad Martinkov
 */

@Transactional
public class UpdateMessageMethodTest extends BaseControllerTest
{

    @Value("${testing.host.origin.local}")
    private String hostOrigin;
    
    @Value("${test.images.location}")
    private String testImagesFolder;

    @LocalServerPort
    private int port;
    
    @Autowired
    MessageController messageController;
    
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MessageRepository messageRepository;
    @Autowired
    UserMapper userMapper;
    @Autowired
    UserRepository userRepository;
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private TestDataHelper testDataHelper;
    
    private MockMvc messageControllerMvc;
    private MockMultipartHttpServletRequestBuilder mvcMultipartBuilder;
    private TestingAuthenticationToken authentication;
    private boolean wasInserted = false;
    private File validPicture;
    private File invalidPicture;
    private MockMultipartFile mockMultipartFile;
    private MockMultipartFile mockMultipartInvalidFile;
    private ListMessageDTO messageForTest;
    
    
    
    @Before
    public void setUp() throws Exception
    {
        
        if (!wasInserted) {
            
            messageControllerMvc = MockMvcBuilders
                .standaloneSetup(messageController)
                .setControllerAdvice(new RestExceptionHandler())
                .build();


            mvcMultipartBuilder = MockMvcRequestBuilders.fileUpload(String.format("%s:%s/message", hostOrigin, port));
            mvcMultipartBuilder.with((MockHttpServletRequest request) -> {
                request.setMethod("PUT");
                return request;
            });
            User user = testDataHelper.createTestUserAndOneHundredMessagesAndReturnUserAuthorDTO();
            wasInserted = true;
            authentication = new TestingAuthenticationToken(user, null);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            validPicture = new File(getClass().getClassLoader().getResource(TestConstants.validTestPicturePath).getFile());
            invalidPicture = new File(getClass().getClassLoader().getResource(TestConstants.invalidTestPicturePath).getFile());
            mockMultipartFile = new MockMultipartFile("little_kitten", new FileInputStream(validPicture));
            mockMultipartInvalidFile = new MockMultipartFile("little_kitten", new FileInputStream(invalidPicture));

            String response = MockMvcBuilders
                    .webAppContextSetup(context)
                    .build()
                    .perform(get(String.format("%s:%s/message", hostOrigin, port))
                    .principal(authentication)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andReturn().getResponse().getContentAsString();
            MessageDTO messageDTO = objectMapper.readValue(response, MessageDTO.class);
            messageForTest = messageDTO.getMessages().get(0);
        }
    }
    
    @Test
    public void getErrorWithoutMockUser() throws Exception {

        MockMvcBuilders
            .webAppContextSetup(context)
            .apply(springSecurity())
            .build()
            .perform(
                    mvcMultipartBuilder
                    .param("id", messageForTest.getId().toString())
                    .param("text", messageForTest.getText())
                    .param("tag", messageForTest.getTag())
            )
            .andExpect(status().isForbidden());
    }

    @Test
    public void putValidMessageWithNotMessageAuthor() throws Exception {

        String changedText = "without tag";
        messageControllerMvc.perform(
                mvcMultipartBuilder
                        .file("file", mockMultipartFile.getBytes())
                        .param("id", messageForTest.getId().toString())
                        .param("text", changedText)
                        .principal(authentication)
        )
                .andExpect(status().isForbidden());
    }

    @Test
    public void putMessageWithEmptyTextAndGetBadRequest() throws Exception {

        String errorResponse = messageControllerMvc.perform(
                mvcMultipartBuilder
                .file("file", mockMultipartFile.getBytes())
                .param("id", messageForTest.getId().toString())
                .param("text", "")
                .param("tag", messageForTest.getTag())
                .principal(authentication)
        )
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn().getResponse().getContentAsString();

        ArrayList subErrorsList = testDataHelper.checkErrorResponseStructureAndReturnDetailedSubErrorList(errorResponse);
        assertThat(subErrorsList.size(), is(1));

        HashMap subErrorHashMap = (HashMap) subErrorsList.get(0);

        assertThat(subErrorHashMap.containsKey("object"), is(true));
        assertThat(subErrorHashMap.get("object"), is("updateMessageDTO"));
        assertThat(subErrorHashMap.containsKey("field"), is(true));
        assertThat(subErrorHashMap.get("field"), is("text"));
        assertThat(subErrorHashMap.containsKey("rejectedValue"), is(true));
        assertThat(subErrorHashMap.get("rejectedValue"), is(""));
        assertThat(subErrorHashMap.containsKey("message"), is(true));
        assertThat(subErrorHashMap.get("message"), is("Please, fill the message"));
    }

    @Test
    public void putMessageWithInvalidFileTypeAndBlankText() throws Exception {

        String errorResponse = messageControllerMvc.perform(
                mvcMultipartBuilder
                        .param("file", "")
                        .param("id", messageForTest.getId().toString())
                        .param("text", "")
                        .param("tag", messageForTest.getTag())
                        .principal(authentication)
        )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        ArrayList subErrorsList = testDataHelper.checkErrorResponseStructureAndReturnDetailedSubErrorList(errorResponse);
        assertThat(subErrorsList.size(), is(2));

        HashMap fileSubErrorHashMap = (HashMap) subErrorsList.get(0);

        assertThat(fileSubErrorHashMap.containsKey("object"), is(true));
        assertThat(fileSubErrorHashMap.get("object"), is("updateMessageDTO"));
        assertThat(fileSubErrorHashMap.containsKey("field"), is(true));
        assertThat(fileSubErrorHashMap.get("field"), is("file"));
        assertThat(fileSubErrorHashMap.containsKey("rejectedValue"), is(true));
        assertThat(fileSubErrorHashMap.get("rejectedValue"), is(""));
        assertThat(fileSubErrorHashMap.containsKey("message"), is(true));
        assertThat((String) fileSubErrorHashMap.get("message"), containsString("Cannot convert value of type 'java.lang.String'"));

        HashMap textSubErrorHashMap = (HashMap) subErrorsList.get(1);

        assertThat(textSubErrorHashMap.containsKey("object"), is(true));
        assertThat(textSubErrorHashMap.get("object"), is("updateMessageDTO"));
        assertThat(textSubErrorHashMap.containsKey("field"), is(true));
        assertThat(textSubErrorHashMap.get("field"), is("text"));
        assertThat(textSubErrorHashMap.containsKey("rejectedValue"), is(true));
        assertThat(textSubErrorHashMap.get("rejectedValue"), is(""));
        assertThat(textSubErrorHashMap.containsKey("message"), is(true));
        assertThat(textSubErrorHashMap.get("message"), is("Please, fill the message"));
    }

    @Test
    public void putMessageWithInvalidTagLength() throws Exception {

        String errorResponse = messageControllerMvc.perform(
                mvcMultipartBuilder
                        .file("file", mockMultipartFile.getBytes())
                        .param("id", messageForTest.getId().toString())
                        .param("text", "Hello")
                        .param("tag", RandomStringUtils.randomAlphabetic(256))
                        .principal(authentication)
        )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        ArrayList subErrorsList = testDataHelper.checkErrorResponseStructureAndReturnDetailedSubErrorList(errorResponse);
        assertThat(subErrorsList.size(), is(1));

        HashMap subErrorHashMap = (HashMap) subErrorsList.get(0);

        assertThat(subErrorHashMap.containsKey("object"), is(true));
        assertThat(subErrorHashMap.get("object"), is("updateMessageDTO"));
        assertThat(subErrorHashMap.containsKey("field"), is(true));
        assertThat(subErrorHashMap.get("field"), is("tag"));

        String rejectedString = (String) subErrorHashMap.get("rejectedValue");
        assertNotNull(rejectedString);
        assertThat(rejectedString.length(), is(256));
        assertThat(subErrorHashMap.containsKey("message"), is(true));
        assertThat(subErrorHashMap.get("message"), is("Tag is too long"));
    }

    @Test
    public void putMessageWithInvalidImage() throws Exception {

        String errorResponse = messageControllerMvc.perform(
                mvcMultipartBuilder
                        .file("file", mockMultipartInvalidFile.getBytes())
                        .param("id", messageForTest.getId().toString())
                        .param("text", "Hello")
                        .param("tag", "some_tag")
                        .principal(authentication)
        )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        ArrayList subErrorsList = testDataHelper.checkErrorResponseStructureAndReturnDetailedSubErrorList(errorResponse);
        assertThat(subErrorsList.size(), is(1));

        HashMap subErrorHashMap = (HashMap) subErrorsList.get(0);

        assertThat(subErrorHashMap.containsKey("object"), is(true));
        assertThat(subErrorHashMap.get("object"), is("updateMessageDTO"));
        assertThat(subErrorHashMap.containsKey("field"), is(true));
        assertThat(subErrorHashMap.get("field"), is("file"));
        assertThat(subErrorHashMap.containsKey("rejectedValue"), is(true));
        assertThat(subErrorHashMap.get("rejectedValue"), is("MultipartFile"));
        assertThat(subErrorHashMap.containsKey("message"), is(true));
        assertThat(subErrorHashMap.get("message"), is("Please, don't upload files with size more than 500 kB!"));
    }

//    @Test
//    public void putValidMessageWithoutTag() throws Exception {
//
//        String changedText = "without tag";
//        messageControllerMvc.perform(
//                mvcMultipartBuilder
//                        .file("file", mockMultipartFile.getBytes())
//                        .param("id", messageForTest.getId().toString())
//                        .param("text", changedText)
//                        .principal(authentication)
//        )
//                .andExpect(status().isOk());
//
//        // Check for empty Optional haven't made cause we are in a test case and it'll throw NPE in this case.
//        // Always check for NPE in any case excepts test cases;)
//        Message changedMessage = messageRepository.findById(messageForTest.getId()).get();
//        assertThat(changedMessage.getText(), is(changedText));
//    }

//    @Test
//    public void putValidMessage() throws Exception {
//
//        messageControllerMvc.perform(
//                mvcMultipartBuilder
//                        .file("file", mockMultipartFile.getBytes())
//                        .param("id", messageForTest.getId().toString())
//                        .param("text", "Hello!!!:) Text has been changed.")
//                        .param("tag", messageForTest.getTag())
//                        .principal(authentication)
//        )
//                .andExpect(status().isOk());
//    }
}