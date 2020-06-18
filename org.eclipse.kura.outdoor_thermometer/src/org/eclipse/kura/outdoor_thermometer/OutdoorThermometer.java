package org.eclipse.kura.outdoor_thermometer;

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
import org.eclipse.kura.cloudconnection.listener.CloudDeliveryListener;
import org.eclipse.kura.cloudconnection.message.KuraMessage;
import org.eclipse.kura.cloudconnection.publisher.CloudPublisher;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutdoorThermometer implements CloudPublisher, ConfigurableComponent, CloudConnectionListener, CloudDeliveryListener {

    private static final Logger logger = LoggerFactory.getLogger(OutdoorThermometer.class);

    private float temperature = 0;
    private static final String TEMPERATURE_TO_WATCH_FOR_NAME = "temperature_to_watch_for";
    private static final String THRESHOLD_NAME = "threshold";
    
    // Publishing Property Names
    private static final String MODE_PROP_NAME = "mode";
    private static final String MODE_PROP_MORE = "More";
    private static final String MODE_PROP_LESS = "Less";
    private static final String PUBLISH_RATE_PROP_NAME = "publish.rate";

    private final ScheduledExecutorService worker;
    private ScheduledFuture<?> handle;
    
    private final ScheduledExecutorService worker_temp;

    private Map<String, Object> properties;
    private final Random random;

    private CloudPublisher cloudPublisher;

    // ----------------------------------------------------------------
    //
    // Dependencies
    //
    // ----------------------------------------------------------------

    public OutdoorThermometer() {
        super();
        this.random = new Random();
        this.worker = Executors.newSingleThreadScheduledExecutor();
        this.worker_temp = Executors.newSingleThreadScheduledExecutor();
    }

    public void setCloudPublisher(CloudPublisher cloudPublisher) {
        this.cloudPublisher = cloudPublisher;
        this.cloudPublisher.registerCloudConnectionListener(OutdoorThermometer.this);
        this.cloudPublisher.registerCloudDeliveryListener(OutdoorThermometer.this);
    }

    public void unsetCloudPublisher(CloudPublisher cloudPublisher) {
        this.cloudPublisher.unregisterCloudConnectionListener(OutdoorThermometer.this);
        this.cloudPublisher.unregisterCloudDeliveryListener(OutdoorThermometer.this);
        this.cloudPublisher = null;
    }

    // ----------------------------------------------------------------
    //
    // Activation APIs
    //
    // ----------------------------------------------------------------

    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        logger.info("Activating Outdoor Thermometer...");
        
        this.worker_temp.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                Thread.currentThread().setName(getClass().getSimpleName());
                doPublish();
                temperature = -2 + random.nextFloat()*(2 - (-2));
            }
        }, 0, 3, TimeUnit.SECONDS);

        this.properties = properties;
        for (Entry<String, Object> property : properties.entrySet()) {
            logger.info("Update - {}: {}", property.getKey(), property.getValue());
        }

        // get the mqtt client for this application
        try {
            // Don't subscribe because these are handled by the default
            // subscriptions and we don't want to get messages twice
            doUpdate(false);
        } catch (Exception e) {
            logger.error("Error during component activation", e);
            throw new ComponentException(e);
        }
        logger.info("Activating Outdoor Thermometer... Done.");
    }

    protected void deactivate(ComponentContext componentContext) {
        logger.debug("Deactivating Outdoor Thermometer...");

        // shutting down the worker and cleaning up the properties
        this.worker.shutdown();
        this.worker_temp.shutdown();

        logger.debug("Deactivating Outdoor Thermometer... Done.");
    }

    public void updated(Map<String, Object> properties) {
        logger.info("Updated Outdoor Thermometer...");

        // store the properties received
        this.properties = properties;
        for (Entry<String, Object> property : properties.entrySet()) {
            logger.info("Update - {}: {}", property.getKey(), property.getValue());
        }

        // try to kick off a new job
        doUpdate(true);
        logger.info("Updated Outdoor Thermometer... Done.");
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
    public void onMessageConfirmed(String messageId) {
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

    /**
     * Called after a new set of properties has been configured on the service
     */
    private void doUpdate(boolean onUpdate) {
        // cancel a current worker handle if one if active
        if (this.handle != null) {
            this.handle.cancel(true);
        }
       

        // schedule a new worker based on the properties of the service
        int pubrate = (Integer) this.properties.get(PUBLISH_RATE_PROP_NAME);
        this.handle = this.worker.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                Thread.currentThread().setName(getClass().getSimpleName());
                doPublish();
            }
        }, 0, pubrate, TimeUnit.SECONDS);
    }

    /**
     * Called at the configured rate to publish the next temperature measurement.
     */
    private void doPublish() {
        if (this.cloudPublisher == null) {
            logger.info("No cloud publisher selected. Cannot publish!");
            return;
        }
        
        try {
            
        	logger.info("(DEBUG) TEMPERATURA: {}", temperature);
        	
            // fetch the publishing configuration from the publishing properties
            String mode = (String) this.properties.get(MODE_PROP_NAME);

            float temperature_to_watch_for = (Float) this.properties.get(TEMPERATURE_TO_WATCH_FOR_NAME);
            float threshold = (Float) this.properties.get(THRESHOLD_NAME);
            
            if((MODE_PROP_MORE.equals(mode) && this.temperature + threshold >= temperature_to_watch_for) ||
            		(MODE_PROP_LESS.equals(mode) && this.temperature - threshold <= temperature_to_watch_for)) {
                KuraPayload payload = new KuraPayload();

                // Timestamp the message
                payload.setTimestamp(new Date());
                payload.addMetric("temperature", this.temperature);
                
                KuraMessage message = new KuraMessage(payload);

                // Publish the message
                try {
                    this.cloudPublisher.publish(message);
                    logger.info("Published message: {}", payload);
                } catch (Exception e) {
                    logger.error("Cannot publish message: {}", message, e);
                }
            }

        } catch (Exception e) {
        	logger.info("ERROR: {}", e);
        }

    }

	@Override
	public String publish(KuraMessage arg0) throws KuraException {
		logger.info("YOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO");
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void registerCloudConnectionListener(CloudConnectionListener arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void registerCloudDeliveryListener(CloudDeliveryListener arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void unregisterCloudConnectionListener(CloudConnectionListener arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void unregisterCloudDeliveryListener(CloudDeliveryListener arg0) {
		// TODO Auto-generated method stub
		
	}
}
