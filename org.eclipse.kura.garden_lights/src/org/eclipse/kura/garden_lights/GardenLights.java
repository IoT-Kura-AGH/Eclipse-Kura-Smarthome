package org.eclipse.kura.garden_lights;

import java.time.LocalTime;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.regex.Pattern;

import org.eclipse.kura.cloudconnection.listener.CloudConnectionListener;
import org.eclipse.kura.cloudconnection.message.KuraMessage;
import org.eclipse.kura.cloudconnection.subscriber.CloudSubscriber;
import org.eclipse.kura.cloudconnection.subscriber.listener.CloudSubscriberListener;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GardenLights implements ConfigurableComponent, CloudSubscriberListener, CloudConnectionListener {
    
	private static final Logger logger = LoggerFactory.getLogger(GardenLights.class);
	private static final String TIME_PATTERN = "([01]?[0-9]|2[0-3]):[0-5][0-9]";
	private Pattern pattern = Pattern.compile(TIME_PATTERN);
	// Publishing Property Names
    private static final String START_TIME_PROP_NAME = "activation_time";
    private static final String STOP_TIME_PROP_NAME = "deactivation_time";
    private static final String POWER = "power";
    private static final String DURATION_TIME_PROP_NAME = "time_duration";
	
	private CloudSubscriber cloudSubscriber;
	
	private ScheduledExecutorService worker;
	
    private Map<String, Object> properties;
    
    
    
    public void setCloudSubscriber(CloudSubscriber cloudSubscriber) {
        this.cloudSubscriber = cloudSubscriber;
        this.cloudSubscriber.registerCloudSubscriberListener(GardenLights.this);
        this.cloudSubscriber.registerCloudConnectionListener(GardenLights.this);
    }

    public void unsetCloudSubscriber(CloudSubscriber cloudSubscriber) {
        this.cloudSubscriber.unregisterCloudSubscriberListener(GardenLights.this);
        this.cloudSubscriber.unregisterCloudConnectionListener(GardenLights.this);
        this.cloudSubscriber = null;
    }
    
    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        logger.info("Activating Lights...");
        this.worker = Executors.newSingleThreadScheduledExecutor();
        this.properties = properties;
        dumpProperties("Activate", properties);
        logger.info("Activating Lights... Done.");
    }

    protected void deactivate(ComponentContext componentContext) {
    	logger.info("Deactivating Lights...");

        // shutting down the worker and cleaning up the properties
        this.worker.shutdown();

        logger.info("Deactivating Lights... Done.");
    }

    public void updated(Map<String, Object> properties) {
        logger.info("Updated Lights...");

        // store the properties received
        this.properties = properties;
        dumpProperties("Update", properties);
        
        logger.info("Updated Lights... Done.");
    }
    

    @Override
    public void onConnectionLost() {
    	logger.info("Connection lost!");

    }

    @Override
    public void onConnectionEstablished() {
    	logger.info("Connection established!");

    }

    @Override
    public void onDisconnected() {
    	logger.info("Disconnected!");

    }
    
    @Override
	public void onMessageArrived(KuraMessage message) {
    	KuraPayload payload = message.getPayload();
    	Map<String, Object> metrics = payload.metrics();
    	if(metrics == null || !metrics.keySet().contains("motionDetected"))
    		return;
    	
    	boolean motionDetected = (boolean) metrics.get("motionDetected");
		logReceivedMessage(message, motionDetected);
		
	}
    

    private static void dumpProperties(final String action, final Map<String, Object> properties) {
        final Set<String> keys = new TreeSet<>(properties.keySet());
        for (final String key : keys) {
            logger.info("{} - {}: {}", action, key, properties.get(key));
        }
    }
    
    private void logReceivedMessage(KuraMessage msg, boolean motionDetected) {
    	if(!motionDetected) {
    		logger.info("\nMOTION NOT DETECTED...");
    		return;
    	}
    	
    	String startTime = (String) this.properties.get(START_TIME_PROP_NAME);
    	String stopTime = (String) this.properties.get(STOP_TIME_PROP_NAME);
    	
    	if(!pattern.matcher(startTime).matches()) {
    		logger.error("Invalid start time {} "
    				+ "\nStart time must be of format HH:mm, for example 20:00.", startTime);
    		return;
    	}
    	
    	if(!pattern.matcher(stopTime).matches()) {
    		logger.error("Invalid stop time {} "
    				+ "\nStop time must be of format HH:mm, for example 8:00.", stopTime);
    		return;
    	}
    	
    	if(startTime.indexOf(':') != 2) {
    		startTime = "0" + startTime;
    	}
    	
    	if(stopTime.indexOf(':') != 2) {
    		stopTime = "0" + stopTime;
    	}
    	
    	LocalTime now = LocalTime.now();
    	LocalTime start = LocalTime.parse(startTime);
    	LocalTime stop = LocalTime.parse(stopTime);
    	
    	logger.info("CURRENT TIME: {}", now.toString());
    	logger.info("START TIME: {}; start: {}", startTime, start.toString());
    	logger.info("STOP TIME: {}; stop: {}", stopTime, stop.toString());
    	
    	Integer power = (Integer) this.properties.get(POWER);
    	Integer duration = (Integer) this.properties.get(DURATION_TIME_PROP_NAME);
    	
    	String logPattern = "\n===============================================\n"
    					  + " CURRENT TIME = {} \n"
    					  + " ACTIVATION TIME = {} \n"
    					  + " DEACTIVATION TIME = {} \n"
    					  + " LIGHT POWER = {} W \n"
    					  + " LIGHTING TIME DURATON = {} s \n"
    					  + " CURRENT STATE = {} \n"
    					  + "===============================================\n";
    	
    	String state = isBetween(start, stop, now) ? "ON" : "OFF";
    	
    	logger.info(logPattern, now, start, stop, power, duration, state);
    	
//    	if(start.isBefore(stop)) {
//    		if(now.isAfter(start) && now.isBefore(stop)) {
//    			logger.info("LIGHT IS ON [1.1]**************************");
//    		} else {
//    			logger.info("LIGHT IS OFF [1.2]..........................");
//    		}
//    	} else {
//    		if(now.isAfter(stop) && now.isBefore(start)) {
//    			logger.info("LIGHT IS OFF [2.1]..........................");
//    		} else {
//    			logger.info("LIGHT IS ON [2.2]..........................");
//    		}
//    	}

        
    }
    
    private boolean isBetween(LocalTime start, LocalTime stop, LocalTime now) {
    	boolean state = false;
    	
    	if(start.isBefore(stop) && now.isAfter(start) && now.isBefore(stop)) {
    		state = true;
    	} else if (stop.isBefore(start) && !(now.isAfter(stop) && now.isBefore(start))) {
    		state = true;
    	}
    	
    	return state;
    }
    
}