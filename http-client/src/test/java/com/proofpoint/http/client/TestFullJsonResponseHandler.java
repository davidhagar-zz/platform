package com.proofpoint.http.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableListMultimap;
import com.proofpoint.http.client.testing.TestingResponse;
import com.proofpoint.json.JsonCodec;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.proofpoint.http.client.FullJsonResponseHandler.JsonResponse;
import static com.proofpoint.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static com.proofpoint.http.client.HttpStatus.INTERNAL_SERVER_ERROR;
import static com.proofpoint.http.client.HttpStatus.OK;
import static com.proofpoint.http.client.testing.TestingResponse.mockResponse;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
public class TestFullJsonResponseHandler
{
    private JsonCodec<User> codec;
    private FullJsonResponseHandler<User> handler;

    @BeforeMethod
    public void setUp()
    {
        codec = JsonCodec.jsonCodec(User.class);
        handler = createFullJsonResponseHandler(codec);
    }

    @Test
    public void testValidJson()
    {
        User user = new User("Joe", 25);
        String json = codec.toJson(user);
        JsonResponse<User> response = handler.handle(null, mockResponse(OK, JSON_UTF_8, json));

        assertTrue(response.hasValue());
        assertEquals(response.getJson(), json);
        assertEquals(response.getValue().getName(), user.getName());
        assertEquals(response.getValue().getAge(), user.getAge());
    }

    @Test
    public void testInvalidJson()
    {
        String json = "{\"age\": \"foo\"}";
        JsonResponse<User> response = handler.handle(null, mockResponse(OK, JSON_UTF_8, json));

        assertFalse(response.hasValue());
        assertEquals(response.getException().getMessage(),
                "Invalid [simple type, class com.proofpoint.http.client.TestFullJsonResponseHandler$User] json string");
    }

    @Test
    public void testInvalidJsonGetValue()
    {
        String json = "{\"age\": \"foo\"}";
        JsonResponse<User> response = handler.handle(null, mockResponse(OK, JSON_UTF_8, json));

        try {
            response.getValue();
            fail("expected exception");
        }
        catch (IllegalStateException e) {
            assertEquals(e.getMessage(), "Response does not contain a JSON value");
            assertEquals(e.getCause(), response.getException());
        }
    }

    @Test
    public void testNonJsonResponse()
    {
        JsonResponse<User> response = handler.handle(null, mockResponse(OK, PLAIN_TEXT_UTF_8, "hello"));

        assertFalse(response.hasValue());
        assertNull(response.getException());
        assertNull(response.getJson());
    }

    @Test
    public void testMissingContentType()
    {
        JsonResponse<User> response = handler.handle(null,
                new TestingResponse(OK, ImmutableListMultimap.<String, String>of(), "hello".getBytes(UTF_8)));

        assertFalse(response.hasValue());
        assertNull(response.getException());
        assertNull(response.getJson());
        assertTrue(response.getHeaders().isEmpty());
    }

    @Test
    public void testJsonErrorResponse()
    {
        String json = "{\"error\": true}";
        JsonResponse<User> response = handler.handle(null, mockResponse(INTERNAL_SERVER_ERROR, JSON_UTF_8, json));

        assertTrue(response.hasValue());
        assertEquals(response.getJson(), json);
        assertNull(response.getValue().getName());
        assertEquals(response.getValue().getAge(), 0);
    }

    static class User
    {
        private final String name;
        private final int age;

        @JsonCreator
        public User(@JsonProperty("name") String name, @JsonProperty("age") int age)
        {
            this.name = name;
            this.age = age;
        }

        @JsonProperty
        public String getName()
        {
            return name;
        }

        @JsonProperty
        public int getAge()
        {
            return age;
        }
    }
}
