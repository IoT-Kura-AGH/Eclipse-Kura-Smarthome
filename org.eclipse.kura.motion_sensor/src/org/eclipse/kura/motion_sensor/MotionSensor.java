package org.eclipse.kura.motion_sensor;

import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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

public class MotionSensor implements ConfigurableComponent, CloudConnectionListener, CloudDeliveryListener{
	private static final Logger logger = LoggerFactory.getLogger(MotionSensor.class);
	private static final float propability = 0.1F; 

    private static final String PUBLISH_RATE_PROP_NAME = "publish.rate";

    private final ScheduledExecutorService worker;
    private ScheduledFuture<?> handle;

    private boolean motionDetected;
    private Map<String, Object> properties;
    private final Random random;

    private CloudPublisher cloudPublisher;

    // ----------------------------------------------------------------
    //
    // Dependencies
    //
    // ----------------------------------------------------------------

    public MotionSensor() {
        super();
        this.random = new Random();
        this.worker = Executors.newSingleThreadScheduledExecutor();
    }

    public void setCloudPublisher(CloudPublisher cloudPublisher) {
        this.cloudPublisher = cloudPublisher;
        this.cloudPublisher.registerCloudConnectionListener(MotionSensor.this);
        this.cloudPublisher.registerCloudDeliveryListener(MotionSensor.this);
    }

    public void unsetCloudPublisher(CloudPublisher cloudPublisher) {
        this.cloudPublisher.unregisterCloudConnectionListener(MotionSensor.this);
        this.cloudPublisher.unregisterCloudDeliveryListener(MotionSensor.this);
        this.cloudPublisher = null;
    }

    // ----------------------------------------------------------------
    //
    // Activation APIs
    //
    // ----------------------------------------------------------------

    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        logger.info("Activating Motion Sensor...");

        this.properties = properties;
        for (Entry<String, Object> property : properties.entrySet()) {
            logger.info("Update - {}: {}", property.getKey(), property.getValue());
        }

        try {
            doUpdate();
        } catch (Exception e) {
            logger.error("Error during component activation", e);
            throw new ComponentException(e);
        }
        logger.info("Activating Motion Sensor... Done.");
    }

    protected void deactivate(ComponentContext componentContext) {
        logger.debug("Deactivating Motion Sensor...");

        this.worker.shutdown();

        logger.debug("Deactivating Motion Sensor... Done.");
    }

    public void updated(Map<String, Object> properties) {
        logger.info("Updated Motion Sensor...");

        this.properties = properties;
        for (Entry<String, Object> property : properties.entrySet()) {
            logger.info("Update - {}: {}", property.getKey(), property.getValue());
        }

        doUpdate();
        logger.info("Updated Motion Sensor... Done.");
    }

    // ----------------------------------------------------------------
    //
    // Cloud Application Callback Methods
    //
    // ----------------------------------------------------------------

    @Override
    public void onConnectionLost() {
        logger.info("Connection lost!");

    }

    @Override
    public void onConnectionEstablished() {
    	logger.info("Connection established!");

    }

    @Override
    public void onMessageConfirmed(String messageId) {
    	logger.info("Message confirmed!");
    }

    @Override
    public void onDisconnected() {
    	logger.info("Disconnected!");

    }

    // ----------------------------------------------------------------
    //
    // Private Methods
    //
    // ----------------------------------------------------------------

    private void doUpdate() {
        if (this.handle != null) {
            this.handle.cancel(true);
        }

        int pubrate = (Integer) this.properties.get(PUBLISH_RATE_PROP_NAME);
        this.handle = this.worker.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                Thread.currentThread().setName(getClass().getSimpleName());
                doPublish();
            }
        }, 0, pubrate, TimeUnit.SECONDS);
    }


    private void doPublish() {
        if (this.cloudPublisher == null) {
            logger.info("No cloud publisher selected. Cannot publish!");
            return;
        }
        
        float randomFloat = random.nextFloat();
        if(randomFloat < propability) {
        	motionDetected = true;
        } else {
        	motionDetected = false;
        }
        
        KuraPayload payload = new KuraPayload();
        
        payload.setTimestamp(new Date());

        payload.addMetric("motionDetected", this.motionDetected);

        KuraMessage message = new KuraMessage(payload);

        try {
            this.cloudPublisher.publish(message);
            logger.info("Published message: {}, with propability = {}", payload, randomFloat);
        } catch (Exception e) {
            logger.error("Cannot publish message: {}", message, e);
        }
    }
}
