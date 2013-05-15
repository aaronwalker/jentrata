<?xml version="1.0" encoding="UTF-8"?>
<S12:Envelope
        xmlns:S12="http://www.w3.org/2003/05/soap-envelope"
        xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
        xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
        xmlns:eb="http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/">
    <S12:Header>
        <eb:Messaging S12:mustUnderstand="true" id="_9ecb9d3c-cef8-4006-ac18-f425c5c7ae3d">
            <eb:UserMessage>
                <eb:MessageInfo>
                    <eb:Timestamp>${.now?iso("UTC")}</eb:Timestamp>
                    <eb:MessageId>${headers.JentrataMessageID}</eb:MessageId>
                </eb:MessageInfo>
                <eb:PartyInfo>
                    <eb:From>
                        <eb:PartyId type="${headers.JentrataFromPartyIdType!'urn:oasis:names:tc:ebcore:partyid-type:iso6523:0088'}">${headers.JentrataFrom}</eb:PartyId>
                        <eb:Role>${headers.JentrataPartyFromRole!"http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/initiator"}</eb:Role>
                    </eb:From>
                    <eb:To>
                        <eb:PartyId type="${headers.JentrataToPartyIdType!'urn:oasis:names:tc:ebcore:partyid-type:iso6523:0088'}">${headers.JentrataTo}</eb:PartyId>
                        <eb:Role>${headers.JentrataPartyToRole!"http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/responder"}</eb:Role>
                    </eb:To>
                </eb:PartyInfo>
                <eb:CollaborationInfo>
                    <#if headers.JentrataAgreementRef?has_content>
                    <eb:AgreementRef>${headers.JentrataAgreementRef}</eb:AgreementRef>
                    </#if>
                    <eb:Service>${headers.JentrataService!"http://docs.oasis-open.org/ebxml-msg/as4/200902/service"}</eb:Service>
                    <eb:Action>${headers.JentrataAction!"http://docs.oasis-open.org/ebxml-msg/as4/200902/action"}</eb:Action>
                    <eb:ConversationId>${headers.JentrataConversationId!headers.JentrataMessageID}</eb:ConversationId>
                </eb:CollaborationInfo>
                <eb:PayloadInfo>
                    <#list body as payload>
                    <eb:PartInfo href="cid:${payload.payloadId}">
                        <#if payload.schema?has_content>
                        <eb:Schema location="${payload.schema}"/>
                        </#if>
                        <eb:PartProperties>
                            <eb:Property name="MimeType">${payload.contentType}</eb:Property>
                            <eb:Property name="CharacterSet">${payload.charset}</eb:Property>
                            <#list payload.partProperties as partProperty>
                            <eb:Property name="${partProperty.name}">${partProperty.value}</eb:Property>
                            </#list>
                        </eb:PartProperties>
                    </eb:PartInfo>
                    </#list>
                </eb:PayloadInfo>
            </eb:UserMessage>
        </eb:Messaging>
     </S12:Header>
    <S12:Body wsu:Id="_f8aa8b55-b31c-4364-94d0-3615ca65aa40"/>
</S12:Envelope>