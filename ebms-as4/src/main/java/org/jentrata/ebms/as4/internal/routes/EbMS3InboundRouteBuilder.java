package org.jentrata.ebms.as4.internal.routes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.component.freemarker.FreemarkerConstants;
import org.jentrata.ebms.EbmsConstants;
import org.jentrata.ebms.MessageStatusType;
import org.jentrata.ebms.MessageType;
import org.jentrata.ebms.internal.messaging.MessageDetector;
import org.jentrata.ebms.messaging.MessageStore;
import org.jentrata.ebms.messaging.SplitAttachmentsToBody;
import org.jentrata.ebms.soap.SoapMessageDataFormat;
import org.jentrata.ebms.soap.SoapPayloadProcessor;

/**
 * Exposes an HTTP endpoint that consumes AS4 Messages
 *
 * @author aaronwalker
 */
public class EbMS3InboundRouteBuilder extends RouteBuilder {

    private String ebmsHttpEndpoint = "jetty:http://0.0.0.0:8081/jentrata/ebms/inbound";
    private String inboundEbmsQueue = "activemq:queue:jentrata_internal_ebms_inbound";
    private String inboundEbmsPayloadQueue = "activemq:queue:jentrata_internal_ebms_inbound_payload";
    private String inboundEbmsSignalsQueue = "activemq:queue:jentrata_internal_ebms_inbound_signals";
    private String securityErrorQueue = "activemq:queue:jentrata_internal_ebms_error";
    private String messgeStoreEndpoint = MessageStore.DEFAULT_MESSAGE_STORE_ENDPOINT;
    private String messageInsertEndpoint = MessageStore.DEFAULT_MESSAGE_INSERT_ENDPOINT;
    private String messageUpdateEndpoint = MessageStore.DEFAULT_MESSAGE_UPDATE_ENDPOINT;
    private String wsseSecurityCheck = "direct:wsseSecurityCheck";
    private String validateTradingPartner = "direct:validatePartner";
    private MessageDetector messageDetector;
    private SoapPayloadProcessor payloadProcessor;

    @Override
    public void configure() throws Exception {

        final Namespaces ns = new Namespaces("S12", "http://www.w3.org/2003/05/soap-envelope")
                .add("eb3", "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/");

        from(ebmsHttpEndpoint)
            .streamCaching()
            .onException(UnsupportedOperationException.class)
                .handled(true)
                .setHeader("X-Jentrata-Version", simple("${sys.jentrataVersion}"))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(405))
                .setHeader("Allow", constant("POST"))
                .to("direct:errorHandler")
            .end()
            .onException(Exception.class)
                .handled(true)
                .log(LoggingLevel.DEBUG, "headers:${headers}\nbody:\n${in.body}")
                .log(LoggingLevel.ERROR, "${exception.message}\n${exception.stacktrace}")
                .setHeader(EbmsConstants.MESSAGE_STATUS,constant(MessageStatusType.FAILED))
                .setHeader(EbmsConstants.MESSAGE_STATUS_DESCRIPTION,simple("${exception.message}"))
                .to(messageUpdateEndpoint)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                .to("direct:errorHandler")
             .end()
            .log(LoggingLevel.INFO, "Request:${headers}")
            .setHeader(EbmsConstants.MESSAGE_DIRECTION, constant(EbmsConstants.MESSAGE_DIRECTION_INBOUND))
            .choice()
                .when(header(Exchange.HTTP_METHOD).isNotEqualTo("POST"))
                    .throwException(new UnsupportedOperationException("Http Method Not Allowed"))
             .end()
            .bean(messageDetector, "parse") //Determine what type of message it is for example SOAP 1.1 or SOAP 1.2 ebms2 or ebms3 etc
            .to(messgeStoreEndpoint) //essentially we claim check the raw incoming message/payload
            .unmarshal(new SoapMessageDataFormat()) //extract the SOAP Envelope as set it has the message body
            .setHeader(EbmsConstants.MESSAGE_STATUS, constant(MessageStatusType.RECEIVED.name()))
            .setHeader(EbmsConstants.MESSAGE_TO, ns.xpath("//eb3:To/eb3:PartyId/text()", String.class))
            .setHeader(EbmsConstants.MESSAGE_FROM, ns.xpath("//eb3:From/eb3:PartyId/text()", String.class))
            .setHeader(EbmsConstants.MESSAGE_SERVICE, ns.xpath("//eb3:CollaborationInfo/eb3:Service/text()", String.class))
            .setHeader(EbmsConstants.MESSAGE_ACTION, ns.xpath("//eb3:CollaborationInfo/eb3:Action/text()", String.class))
            .setHeader(EbmsConstants.MESSAGE_CONVERSATION_ID, ns.xpath("//eb3:CollaborationInfo/eb3:ConversationId/text()", String.class))
            .to("direct:lookupCpaId")
            .to(messageInsertEndpoint) //create a message entry in the message store to track the state of the message
            .to(validateTradingPartner)
            .to(wsseSecurityCheck)
            .choice()
                .when(header(EbmsConstants.SECURITY_CHECK).isEqualTo(Boolean.FALSE))
                    .to("direct:handleSecurityException")
                .otherwise()
                    .to("direct:processPayloads")
                    .to(messageUpdateEndpoint)
                    .setBody(constant(null))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(204))
                    .to("direct:removeHeaders")
                    .setHeader("X-Jentrata-Version", simple("${sys.jentrataVersion}"))
            .end()
        .routeId("_jentrataEbmsInbound");

        from("direct:processPayloads")
            .convertBodyTo(String.class)
             .choice()
                .when(header(EbmsConstants.MESSAGE_TYPE).isEqualTo(MessageType.SIGNAL_MESSAGE))
                    .inOnly(inboundEbmsSignalsQueue)
                .when(header(EbmsConstants.MESSAGE_TYPE).isEqualTo(MessageType.SIGNAL_MESSAGE_WITH_USER_MESSAGE))
                    .inOnly(inboundEbmsSignalsQueue)
                .when(header(EbmsConstants.MESSAGE_TYPE).isEqualTo(MessageType.USER_MESSAGE))
                    .inOnly(inboundEbmsQueue)
                    .setHeader(SplitAttachmentsToBody.ORIGINAL_MESSAGE_BODY,body())
                    .setBody(xpath("//*[local-name()='Body']/*[1]"))
                    .convertBodyTo(String.class)
                    .split(new SplitAttachmentsToBody(true, false, true))
                        .bean(payloadProcessor)
                        .removeHeader(SplitAttachmentsToBody.ORIGINAL_MESSAGE_BODY)
                        .inOnly(inboundEbmsPayloadQueue)
        .routeId("_jentrataEbmsPayloadProcessing");

        from("direct:handleSecurityException")
            .setHeader(EbmsConstants.SECURITY_ERROR_CODE, simple("${headers.JentrataSecurityResults?.errorCode}"))
            .setHeader(EbmsConstants.SECURITY_ERROR_MESSAGE,simple("${headers.JentrataSecurityResults?.message}"))
            .log(LoggingLevel.INFO, "Security Exception for msgId:${headers.JentrataMessageID} - errorCode:${headers.JentrataSecurityErrorCode} - ${headers.JentrataSecurityErrorMessage}")
            .convertBodyTo(String.class)
            .choice()
                .when(simple("${headers?.JentrataCPA?.security?.sendReceiptReplyPattern.name()} == 'Callback'"))
                    .inOnly(securityErrorQueue)
                    .setBody(constant(null))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(204))
                .otherwise()
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                    .to(securityErrorQueue)
            .end()
            .to("direct:removeHeaders")
            .setHeader("X-Jentrata-Version", simple("${sys.jentrataVersion}"))
        .routeId("_jentrataHandleSecurityException");

        from("direct:errorHandler")
            .to("direct:removeHeaders")
            .setHeader("CamelException",simple("${exception.message}"))
            .setHeader("CamelExceptionStackTrace",simple("${exception.stacktrace}"))
            .setHeader("X-JentrataVersion",simple("${sys.jentrataVersion}"))
            .setHeader(FreemarkerConstants.FREEMARKER_RESOURCE_URI, simple("html/${headers.CamelHttpResponseCode}.html"))
            .to("freemarker:html/500.html")
        .end()
        .routeId("_jentrataErrorHandler");

        from("direct:removeHeaders")
            .removeHeaders("Jentrata*")
            .removeHeaders("Accept*")
            .removeHeaders("Content*")
            .removeHeader("Host")
            .removeHeader("User-Agent")
            .removeHeader("Origin")
            .removeHeader("Cookie")
            .removeHeader("JSESSIONID")
            .removeHeader("breadcrumbId")
        .routeId("_jentrataRemoveHeaders");
    }

    public String getEbmsHttpEndpoint() {
        return ebmsHttpEndpoint;
    }

    public void setEbmsHttpEndpoint(String ebmsHttpEndpoint) {
        this.ebmsHttpEndpoint = ebmsHttpEndpoint;
    }

    public String getInboundEbmsQueue() {
        return inboundEbmsQueue;
    }

    public void setInboundEbmsQueue(String inboundEbmsQueue) {
        this.inboundEbmsQueue = inboundEbmsQueue;
    }

    public String getInboundEbmsPayloadQueue() {
        return inboundEbmsPayloadQueue;
    }

    public void setInboundEbmsPayloadQueue(String inboundEbmsPayloadQueue) {
        this.inboundEbmsPayloadQueue = inboundEbmsPayloadQueue;
    }

    public String getInboundEbmsSignalsQueue() {
        return inboundEbmsSignalsQueue;
    }

    public void setInboundEbmsSignalsQueue(String inboundEbmsSignalsQueue) {
        this.inboundEbmsSignalsQueue = inboundEbmsSignalsQueue;
    }

    public String getSecurityErrorQueue() {
        return securityErrorQueue;
    }

    public void setSecurityErrorQueue(String securityErrorQueue) {
        this.securityErrorQueue = securityErrorQueue;
    }

    public String getMessgeStoreEndpoint() {
        return messgeStoreEndpoint;
    }

    public void setMessgeStoreEndpoint(String messgeStoreEndpoint) {
        this.messgeStoreEndpoint = messgeStoreEndpoint;
    }

    public String getMessageInsertEndpoint() {
        return messageInsertEndpoint;
    }

    public void setMessageInsertEndpoint(String messageInsertEndpoint) {
        this.messageInsertEndpoint = messageInsertEndpoint;
    }

    public String getMessageUpdateEndpoint() {
        return messageUpdateEndpoint;
    }

    public void setMessageUpdateEndpoint(String messageUpdateEndpoint) {
        this.messageUpdateEndpoint = messageUpdateEndpoint;
    }

    public String getValidateTradingPartner() {
        return validateTradingPartner;
    }

    public void setValidateTradingPartner(String validateTradingPartner) {
        this.validateTradingPartner = validateTradingPartner;
    }

    public String getWsseSecurityCheck() {
        return wsseSecurityCheck;
    }

    public void setWsseSecurityCheck(String wsseSecurityCheck) {
        this.wsseSecurityCheck = wsseSecurityCheck;
    }

    public MessageDetector getMessageDetector() {
        return messageDetector;
    }

    public void setMessageDetector(MessageDetector messageDetector) {
        this.messageDetector = messageDetector;
    }

    public SoapPayloadProcessor getPayloadProcessor() {
        return payloadProcessor;
    }

    public void setPayloadProcessor(SoapPayloadProcessor payloadProcessor) {
        this.payloadProcessor = payloadProcessor;
    }
}
