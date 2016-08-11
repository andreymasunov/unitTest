package de.home24.middleware.octestframework.components;

import java.util.HashMap;
import java.util.Map;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.stereotype.Component;

/**
 * DAO implementation for accessing JMS resources.
 * 
 * @author svb
 *
 */
@Component
public class MessagingDao {

    public enum JmsModule {
	SALES_ORDER, PURCHASE_ORDER, SII, EDIFACT, LOGGING;
    }

    private Map<JmsModule, JmsTemplate> moduleToTemplate;

    public MessagingDao() {

	moduleToTemplate = new HashMap<>();
    }

    /**
     * Send a TextMessage to the defined queue.
     * 
     * @param pJmsModule
     *            the {@link JmsModule} to which the current destination belongs
     *            to
     * @param pDestinationName
     *            the JNDI name of the destination queue
     * @param pMessage
     *            the message as a string representation
     */
    public void writeToQueue(JmsModule pJmsModule, String pDestinationName, final String pMessage) {

	moduleToTemplate.get(pJmsModule).send(pDestinationName, new MessageCreator() {

	    @Override
	    public Message createMessage(Session pSession) throws JMSException {
		return pSession.createTextMessage(pMessage);
	    }
	});
    }
    
    /**
     * 
     * Polls a specific queue and retrieves a single message from it
     * 
     * @param pJmsModule the {@link JmsModule} to which the current destination belongs
     *            to
     * @param pDestinationName the JNDI name of the destination queue
     * @param pReceiveTimeout the timeout to use
     * @return the message as a string representation or null if within the given timeout no message can be retrieved
     */
    public Message readFromQueue(JmsModule pJmsModule, String pDestinationName, Long pReceiveTimeout)
    {
    	moduleToTemplate.get(pJmsModule).setReceiveTimeout(pReceiveTimeout);
    	return moduleToTemplate.get(pJmsModule).receive(pDestinationName);
    }

    @Autowired
    protected void setSalesOrderTemplate(@Qualifier("salesOrderQueueTemplate") JmsTemplate pJmsTemplate) {
	moduleToTemplate.put(JmsModule.SALES_ORDER, pJmsTemplate);
    }

    @Autowired
    protected void setPurchaseOrderTemplate(
	    @Qualifier("purchaseOrderQueueTemplate") JmsTemplate pJmsTemplate) {
	moduleToTemplate.put(JmsModule.PURCHASE_ORDER, pJmsTemplate);
    }

    @Autowired
    protected void setSiiTemplate(@Qualifier("siiQueueTemplate") JmsTemplate pJmsTemplate) {
	moduleToTemplate.put(JmsModule.SII, pJmsTemplate);
    }

     @Autowired
     protected void setEdifactTemplate(@Qualifier("edifactQueueTemplate")JmsTemplate pJmsTemplate) {
     moduleToTemplate.put(JmsModule.EDIFACT, pJmsTemplate);
     }

    @Autowired
    protected void setLoggingTemplate(@Qualifier("loggingQueueTemplate") JmsTemplate pJmsTemplate) {
	moduleToTemplate.put(JmsModule.LOGGING, pJmsTemplate);
    }
}
