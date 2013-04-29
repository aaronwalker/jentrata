package org.jentrata.ebms.as4.internal.routes;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.jentrata.ebms.EbmsConstants;
import org.jentrata.ebms.MessageType;
import org.jentrata.ebms.cpa.CPARepository;
import org.jentrata.ebms.cpa.PartnerAgreement;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for org.jentrata.ebms.as4.internal.routes.EbmsOutboundRouteBuilder
 *
 * @author aaronwalker
 */
public class EbmsOutboundRouteBuilderTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:agreement1")
    protected MockEndpoint mockAgreement1;


    @EndpointInject(uri = "mock:agreement2")
    protected MockEndpoint mockAgreement2;

    @Test
    public void testSendMessageToPartner() throws Exception {

        mockAgreement1.setExpectedMessageCount(1);
        mockAgreement2.setExpectedMessageCount(1);

        sendMessage("agreement1",
                "simple-as4-user-message.txt",
                "Multipart/Related; boundary=\"----=_Part_7_10584188.1123489648993\"; type=\"application/soap+xml\"; start=\"<soapPart@jentrata.org>\"",
                "2011-921@5209999001264.jentrata.org",
                MessageType.USER_MESSAGE);

        sendMessage("agreement2",
                "simple-as4-receipt.xml",
                EbmsConstants.SOAP_XML_CONTENT_TYPE,
                "someuniqueid@receiver.jentrata.org",
                MessageType.SIGNAL_MESSAGE_WITH_USER_MESSAGE);

        assertMockEndpointsSatisfied();
    }

    private void sendMessage(String cpaId, String filename, String contentType, String msgId, MessageType type) throws Exception {
        Exchange request = new DefaultExchange(context());
        request.getIn().setHeader(EbmsConstants.CONTENT_TYPE,contentType);
        request.getIn().setHeader(EbmsConstants.CPA_ID,cpaId);
        request.getIn().setHeader(EbmsConstants.MESSAGE_ID,msgId);
        request.getIn().setHeader(EbmsConstants.MESSAGE_TYPE, type);
        request.getIn().setHeader(EbmsConstants.MESSAGE_DIRECTION,EbmsConstants.MESSAGE_DIRECTION_OUTBOUND);

        request.getIn().setBody(new FileInputStream(fileFromClasspath("simple-as4-user-message.txt")));
        Exchange response = context().createProducerTemplate().send("direct:testOutboundEbmsQueue",request);


    }

    @Override
    protected RouteBuilder [] createRouteBuilders() throws Exception {
        EbmsOutboundRouteBuilder routeBuilder = new EbmsOutboundRouteBuilder();
        routeBuilder.setOutboundEbmsQueue("direct:testOutboundEbmsQueue");
        routeBuilder.setCpaRepository(new DummyCPARepository());
        return new RouteBuilder[] {
                routeBuilder,
                new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from("direct:agreement1")
                            .to(mockAgreement1.getEndpointUri())
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(204))
                            .setBody(constant(null))
                        .routeId("mockAgreement1");
                        from("direct:agreement2")
                            .to(mockAgreement2.getEndpointUri())
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                            .setBody(constant(null))
                        .routeId("mockAgreement2");
                    }
                }
        };
    }

    protected static File fileFromClasspath(String filename) {
        File file = new File(Thread.currentThread().getContextClassLoader().getResource(filename).getFile());
        return file;
    }

    private class DummyCPARepository implements CPARepository {
        @Override
        public List<PartnerAgreement> getActivePartnerAgreements() {
            PartnerAgreement agreement1 = new PartnerAgreement();
            agreement1.setCPAId("agreement1");
            agreement1.setTransportReceiverEndpoint("direct:agreement1");
            PartnerAgreement agreement2 = new PartnerAgreement();
            agreement2.setCPAId("agreement2");
            agreement2.setTransportReceiverEndpoint("direct:agreement2");
            return Arrays.asList(agreement1,agreement2);
        }
    }
}
