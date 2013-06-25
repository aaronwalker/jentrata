package org.jentrata.ebms.as4.internal.routes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.io.IOUtils;
import org.jentrata.ebms.EbmsConstants;
import org.jentrata.ebms.MessageType;
import org.jentrata.ebms.cpa.InvalidPartnerAgreementException;
import org.jentrata.ebms.cpa.PartnerAgreement;
import org.jentrata.ebms.messaging.MessageStore;
import org.jentrata.ebms.soap.SoapMessageDataFormat;
import org.jentrata.ebms.utils.EbmsUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Pickup outbound messages generates the ebMS envelope
 *
 * @author aaronwalker
 */
public class  EbmsOutboundMessageRouteBuilder extends RouteBuilder {

    private String deliveryQueue = "activemq:queue:jentrata_as4_outbound";
    private String outboundEbmsQueue = "activemq:queue:jentrata_internal_ebms_outbound";
    private String messgeStoreEndpoint = MessageStore.DEFAULT_MESSAGE_STORE_ENDPOINT;
    private String messageInsertEndpoint = MessageStore.DEFAULT_MESSAGE_INSERT_ENDPOINT;
    private String wsseSecurityAddEndpoint = "direct:wsseAddSecurityToHeader";

    @Override
    public void configure() throws Exception {

        from(deliveryQueue)
            .onException(InvalidPartnerAgreementException.class)
                .log(LoggingLevel.DEBUG, "headers:${headers}\nbody:\n${in.body}")
                .log(LoggingLevel.ERROR, "${exception.message}\n${exception.stacktrace}")
                .handled(true)
            .end()
            .to("direct:lookupCpaId")
            .choice()
                .when(header(EbmsConstants.CPA_ID).isEqualTo(EbmsConstants.CPA_ID_UNKNOWN))
                    .throwException(new InvalidPartnerAgreementException("unable to find matching partner agreement"))
                .otherwise()
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {

                            PartnerAgreement agreement = exchange.getIn().getHeader(EbmsConstants.CPA,PartnerAgreement.class);

                            String body = exchange.getIn().getBody(String.class);
                            String contentType = exchange.getIn().getHeader(EbmsConstants.CONTENT_TYPE, String.class);
                            String contentCharset = exchange.getIn().getHeader(EbmsConstants.CONTENT_CHAR_SET, "UTF-8", String.class);
                            String payloadId = exchange.getIn().getHeader(EbmsConstants.PAYLOAD_ID,agreement.getPayloadService().getPayloadId(), String.class);
                            String schema = exchange.getIn().getHeader(EbmsConstants.MESSAGE_PAYLOAD_SCHEMA, String.class);
                            String compressionType = exchange.getIn().getHeader(EbmsConstants.PAYLOAD_COMPRESSION,agreement.getPayloadService().getCompressionType().getType(),String.class);
                            List<Map<String, Object>> partProperties = extractPartProperties(exchange.getIn().getHeader(EbmsConstants.MESSAGE_PART_PROPERTIES, String.class));

                            List<Map<String, Object>> payloads = new ArrayList<>();
                            Map<String, Object> payloadMap = new HashMap<>();
                            payloadMap.put("payloadId", payloadId);
                            payloadMap.put("contentType", contentType);
                            payloadMap.put("charset", contentCharset);
                            payloadMap.put("partProperties", partProperties);
                            payloadMap.put("schema", schema);
                            payloadMap.put("compressionType",compressionType);
                            if(compressionType != null && compressionType.length() > 0) {
                                payloadMap.put("content", EbmsUtils.compress(compressionType, body.getBytes(contentCharset)));
                            } else {
                                payloadMap.put("content", body.getBytes(contentCharset));
                            }
                            payloads.add(payloadMap);
                            exchange.getIn().setBody(payloads);
                        }
                    })
                    .setHeader(EbmsConstants.MESSAGE_ID, simple("${bean:uuidGenerator.generateId}"))
                    .setHeader(EbmsConstants.MESSAGE_TYPE, constant(MessageType.USER_MESSAGE))
                    .setHeader(EbmsConstants.MESSAGE_DIRECTION, constant(EbmsConstants.MESSAGE_DIRECTION_OUTBOUND))
                    .setHeader(EbmsConstants.MESSAGE_PAYLOADS,body())
                    .to("freemarker:templates/soap-envelope.ftl")
                    .to(wsseSecurityAddEndpoint)
                    .convertBodyTo(String.class)
                    .marshal(new SoapMessageDataFormat())
                    .to(messgeStoreEndpoint)
                    .to(messageInsertEndpoint)
                    .to(outboundEbmsQueue)
        .routeId("_jentrataEbmsGenerateMessage");

    }

    private List<Map<String, Object>> extractPartProperties(String partProperties) {
        List<Map<String,Object>> properites = new ArrayList<>();
        if(partProperties != null && partProperties.length() > 0) {
            String [] propertyArray = partProperties.split(";");
            for (String property : propertyArray) {
                String [] value = property.split("=");
                Map<String,Object> propertyMap = new HashMap<>();
                propertyMap.put("name",value[0]);
                propertyMap.put("value",value[1]);
                properites.add(propertyMap);
            }
        }
        return properites;
    }

    public String getDeliveryQueue() {
        return deliveryQueue;
    }

    public void setDeliveryQueue(String deliveryQueue) {
        this.deliveryQueue = deliveryQueue;
    }

    public String getOutboundEbmsQueue() {
        return outboundEbmsQueue;
    }

    public void setOutboundEbmsQueue(String outboundEbmsQueue) {
        this.outboundEbmsQueue = outboundEbmsQueue;
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

    public String getWsseSecurityAddEndpoint() {
        return wsseSecurityAddEndpoint;
    }

    public void setWsseSecurityAddEndpoint(String wsseSecurityAddEndpoint) {
        this.wsseSecurityAddEndpoint = wsseSecurityAddEndpoint;
    }
}
