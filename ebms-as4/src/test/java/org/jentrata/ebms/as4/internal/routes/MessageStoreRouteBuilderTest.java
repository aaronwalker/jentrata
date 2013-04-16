package org.jentrata.ebms.as4.internal.routes;

import org.apache.camel.Body;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.io.IOUtils;
import org.jentrata.ebms.messaging.MessageStore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;

/**
 * Unit tests for org.jentrata.ebms.as4.internal.routes.MessageStoreRouteBuilder
 *
 * @author aaronwalker
 */
public class MessageStoreRouteBuilderTest extends CamelTestSupport {

    private DummyMessageStore messageStore;

    @Test
    public void testMessageStore() throws IOException {
        Exchange request = new DefaultExchange(context());
        request.getIn().setBody(new ByteArrayInputStream("test".getBytes()));
        Exchange response = context().createProducerTemplate().send(MessageStore.DEFAULT_MESSAGE_STORE_ENDPOINT,request);

        String msgId = response.getIn().getHeader(MessageStore.JENTRATA_MESSAGE_ID, String.class);
        String msgStoreRef = response.getIn().getHeader(MessageStore.MESSAGE_STORE_REF,String.class);
        assertThat(msgId,equalTo(request.getIn().getMessageId()));
        assertThat(IOUtils.toString(messageStore.findByMessageRef(msgStoreRef)),equalTo("test"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        MessageStoreRouteBuilder routeBuilder = new MessageStoreRouteBuilder();
        messageStore = new DummyMessageStore();
        routeBuilder.setMessageStore(messageStore);
        return routeBuilder;
    }

    private static class DummyMessageStore implements MessageStore {

        private Map<String, InputStream> messageStore = new LinkedHashMap<>();

        @Override
        public void store(@Body InputStream input, Exchange exchange) {
            messageStore.put(exchange.getIn().getMessageId(),input);
            exchange.getIn().setHeader(MESSAGE_STORE_REF,exchange.getIn().getMessageId());
            exchange.getIn().setHeader(JENTRATA_MESSAGE_ID,exchange.getIn().getMessageId());
        }

        @Override
        public InputStream findByMessageRef(Object messageRef) {
            return messageStore.get(messageRef);
        }
    }
}
