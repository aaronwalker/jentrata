package org.jentrata.ebms.cpa;

import org.apache.camel.Headers;

import java.util.List;
import java.util.Map;

/**
 * A repository of CPA agreements
 *
 * @author aaronwalker
 */
public interface CPARepository {

    /**
     * Gets all the Partner Agreements in the Repository
     *
     * @return all the configured partner agreements in the repository
     */
    List<PartnerAgreement> getPartnerAgreements();

    /**
     * Gets only the active Partner Agreements
     *
     * @return only the partner agreements that are marked as being active
     */
    List<PartnerAgreement> getActivePartnerAgreements();

    /**
     * Finds a active partner agreement that has the service/action defined
     *
     * @param service the service name
     * @param action the corresponding service action
     * @return the partner agreement that has the service/action defined
     */
    PartnerAgreement findByServiceAndAction(final String service, final String action);

    /**
     * Returns true if a valid partner agreements exists matching the
     * service/action combination contained the fields
     *
     * @param fields message header fields from the incoming message
     * @return
     */
    boolean isValidPartnerAgreement(@Headers final Map<String, Object> fields);

}
