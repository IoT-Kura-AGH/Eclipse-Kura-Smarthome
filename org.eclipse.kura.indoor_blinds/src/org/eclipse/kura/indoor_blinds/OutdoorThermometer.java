package org.eclipse.kura.indoor_blinds;

import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.cloudconnection.listener.CloudConnectionListener;
import org.eclipse.kura.cloudconnection.message.KuraMessage;
import org.eclipse.kura.cloudconnection.subscriber.CloudSubscriber;
import org.eclipse.kura.cloudconnection.subscriber.listener.CloudSubscriberListener;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutdoorThermometer implements ConfigurableComponent, CloudConnectionListener, CloudSubscriberListener {

    private static final Logger logger = LoggerFactory.getLogger(OutdoorThermometer.class);
    
    private boolean blinds_opened = false;

    private final ScheduledExecutorService worker;
    private ScheduledFuture<?> handle;

    private Map<String, Object> properties;

    private CloudSubscriber cloudSubscriber;

    // ----------------------------------------------------------------
    //
    // Dependencies
    //
    // ----------------------------------------------------------------

    public OutdoorThermometer() {
        super();
        this.worker = Executors.newSingleThreadScheduledExecutor();
        logger.info("CREAT.");
    }

	public void setCloudSubscriber(CloudSubscriber cloudSubscriber) {
		logger.info("ALLSET.");
		this.cloudSubscriber = cloudSubscriber;
		this.cloudSubscriber.registerCloudSubscriberListener(OutdoorThermometer.this);
		this.cloudSubscriber.registerCloudConnectionListener(OutdoorThermometer.this);
	}

	public void unsetCloudSubscriber(CloudSubscriber cloudSubscriber) {
		this.cloudSubscriber.unregisterCloudSubscriberListener(OutdoorThermometer.this);
		this.cloudSubscriber.unregisterCloudConnectionListener(OutdoorThermometer.this);
		this.cloudSubscriber = null;
	}

    // ----------------------------------------------------------------
    //
    // Activation APIs
    //
    // ----------------------------------------------------------------

    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        logger.info("Activating Indoor Blinds...");

        this.properties = properties;
        for (Entry<String, Object> property : properties.entrySet()) {
            logger.info("Update - {}: {}", property.getKey(), property.getValue());
        }

        logger.info("Activating Indoor Blinds... Done.");
    }

    protected void deactivate(ComponentContext componentContext) {
        logger.debug("Deactivating Indoor Blinds...");

        // shutting down the worker and cleaning up the properties
        this.worker.shutdown();

        logger.debug("Deactivating Indoor Blinds... Done.");
    }

    public void updated(Map<String, Object> properties) {
        logger.info("Updated Indoor Blinds...");

        // store the properties received
        this.properties = properties;
        for (Entry<String, Object> property : properties.entrySet()) {
            logger.info("Update - {}: {}", property.getKey(), property.getValue());
        }

        logger.info("Updated Indoor Blinds... Done.");
    }

    // ----------------------------------------------------------------
    //
    // Cloud Application Callback Methods
    //
    // ----------------------------------------------------------------

    @Override
    public void onConnectionLost() {
        // TODO Auto-generated method stub

    }

    @Override
    public void onConnectionEstablished() {
        // TODO Auto-generated method stub

    }

    @Override
    public void onDisconnected() {
        // TODO Auto-generated method stub

    }

    // ----------------------------------------------------------------
    //
    // Private Methods
    //
    // ----------------------------------------------------------------


//	@Override
//	public void registerCloudConnectionListener(CloudConnectionListener arg0) {
//		// TODO Auto-generated method stub
//		
//	}
//
//	@Override
//	public void unregisterCloudConnectionListener(CloudConnectionListener arg0) {
//		// TODO Auto-generated method stub
//		
//	}
//
//	@Override
//	public void registerCloudSubscriberListener(CloudSubscriberListener arg0) {
//		// TODO Auto-generated method stub
//		
//	}
//
//	@Override
//	public void unregisterCloudSubscriberListener(CloudSubscriberListener arg0) {
//		// TODO Auto-generated method stub
//		
//	}
	
	@Override
	public void onMessageArrived(KuraMessage msg) {
		if(this.blinds_opened) {
			logger.info("Closing blinds.");
		} else {
			logger.info("Opening blinds.");
		}
		
		this.blinds_opened = ! this.blinds_opened;
	}
}
