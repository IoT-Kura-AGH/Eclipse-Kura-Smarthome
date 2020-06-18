package org.eclipse.kura.air_conditioner;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.kura.cloudconnection.listener.CloudConnectionListener;
import org.eclipse.kura.cloudconnection.message.KuraMessage;
import org.eclipse.kura.cloudconnection.subscriber.CloudSubscriber;
import org.eclipse.kura.cloudconnection.subscriber.listener.CloudSubscriberListener;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AirConditioner implements ConfigurableComponent, CloudConnectionListener, CloudSubscriberListener {

    private static final Logger logger = LoggerFactory.getLogger(AirConditioner.class);

    private static final String POWER = "power";
    private static final String POWER_OFF = "off";
    private static final String POWER_ON = "on";
    
    private static final String MODE = "mode";
    private static final String MODE_AUTO = "auto";
    private static final String MODE_COOLING = "cooling";
    private static final String MODE_WARMING = "warming";
    private static final String MODE_VENTILATION = "ventilation";
    
    private static final String TARGET_TEMPERATURE = "target_temperature";
    
    private static final String TEMP_FOR_INFO = "temperature_for_information";
    private static final String TEMP_FOR_INFO_SET = "set";
    private static final String TEMP_FOR_INFO_THERMOMETER = "thermometer";
    
    private static final String TARGET_FAN_SPEED = "target_fan_speed";
    private static final String TARGET_FAN_SPEED_AUTO = "auto";
    private static final String TARGET_FAN_SPEED_LOW = "low";
    private static final String TARGET_FAN_SPEED_MEDIUM = "medium";
    private static final String TARGET_FAN_SPEED_HIGH = "high";

    private final ScheduledExecutorService worker;
    private ScheduledFuture<?> handle;

    private Map<String, Object> properties;

    private CloudSubscriber cloudSubscriber;
    
    private String power = POWER_OFF;
    private String mode = MODE_AUTO;
    private float targetTemp = 0.0f;
    private String tempForInfo = TEMP_FOR_INFO_SET;
    private String targetFanSpeed = TARGET_FAN_SPEED_AUTO;
    
    private String internalMode = MODE_COOLING;
    private float thermoTemp = 0.0f;
    private int fanSpeed = 0; 
    
    private int timeToEndOfSwitchingMode = 0;

    // ----------------------------------------------------------------
    //
    // Dependencies
    //
    // ----------------------------------------------------------------

    public AirConditioner() {
        super();
        this.worker = Executors.newSingleThreadScheduledExecutor();
    }

    public void setCloudSubscriber(CloudSubscriber cloudSubscriber) {
    	logger.info("Registration Air conditioner...");
        this.cloudSubscriber = cloudSubscriber;
        this.cloudSubscriber.registerCloudConnectionListener(AirConditioner.this);
        this.cloudSubscriber.registerCloudSubscriberListener(AirConditioner.this);
    	logger.info("Registration Air conditioner... Done.");
    }

    public void unsetCloudSubscriber(CloudSubscriber cloudSubscriber) {
    	logger.info("Unregistration Air conditioner...");
        this.cloudSubscriber.unregisterCloudConnectionListener(AirConditioner.this);
        this.cloudSubscriber.unregisterCloudSubscriberListener(AirConditioner.this);
        this.cloudSubscriber = null;
    	logger.info("Unregistration Air conditioner... Done.");
    }

    // ----------------------------------------------------------------
    //
    // Activation APIs
    //
    // ----------------------------------------------------------------

    private void adjustFanSpeed(int target) {
    	if(fanSpeed > target)
    		fanSpeed -= 25;
    	if(fanSpeed < target)
    		fanSpeed += 50;
    }
    
    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        logger.info("Activating Air conditioner...");
        
        this.worker.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
            	if(power.equals(POWER_ON))
            	{
            		if(!mode.equals(MODE_AUTO)) {
            			if(!mode.equals(internalMode)) {
	                		timeToEndOfSwitchingMode = 60;
	                		internalMode = mode;
            			}
            		} else {
            			float tempDelta = targetTemp - thermoTemp;
            			if(tempDelta > 2.0f) {
            				if(!internalMode.contentEquals(MODE_WARMING)) {
		                		timeToEndOfSwitchingMode = 60;
		                		internalMode = MODE_WARMING;
            				}
            			}
            			if(tempDelta < -2.0f) {
            				if(!internalMode.contentEquals(MODE_COOLING)) {
		                		timeToEndOfSwitchingMode = 60;
		                		internalMode = MODE_COOLING;
            				}
            			}
            		}
            		if(timeToEndOfSwitchingMode == 0)
            		{
		                if(!targetFanSpeed.equals(TARGET_FAN_SPEED_AUTO)) {
		                	if(targetFanSpeed.contentEquals(TARGET_FAN_SPEED_LOW)) {
		                		adjustFanSpeed(500);
		                	}
		                	if(targetFanSpeed.contentEquals(TARGET_FAN_SPEED_MEDIUM)) {
		                		adjustFanSpeed(1000);
		                	}
		                	if(targetFanSpeed.contentEquals(TARGET_FAN_SPEED_HIGH)) {
		                		adjustFanSpeed(1500);
		                	}
		                } else {
		                	if(!internalMode.equals(MODE_VENTILATION)) {
		                		boolean fanOn = false;
		                		if(internalMode.equals(MODE_COOLING)) {
		                			if(thermoTemp > targetTemp)
		                				fanOn = true;
		                		}
		                		if(internalMode.equals(MODE_WARMING)) {
		                			if(thermoTemp < targetTemp)
		                				fanOn = true;
		                		}
		                		if(fanOn) {
		                			int tempDelta = (int) Math.abs(targetTemp - thermoTemp);
		                			if(tempDelta > 10)
		                				tempDelta = 10;
		                			adjustFanSpeed(500 + tempDelta * 100);
		                		}
		                	} else {
		                		adjustFanSpeed(500);
		                	}
		                		
		                }
            		} else {
	                	timeToEndOfSwitchingMode--;
            			adjustFanSpeed(0);
            		}
            		int tempToDisplay = 0;
            		if(tempForInfo.equals(TEMP_FOR_INFO_SET))
            			tempToDisplay = (int) targetTemp;
            		if(tempForInfo.equals(TEMP_FOR_INFO_THERMOMETER))
            			tempToDisplay = (int) thermoTemp;
            		logger.info("DISPLAY OF THE AIR CONDITIONER: " + tempToDisplay + " degrees of Celcius.");
            	} else {
        			adjustFanSpeed(0);
            	}
        		logger.info("[DEBUG] Information about air conditioner: =====================\n"
        				+ "Power: " + power + ";\n"
        				+ "Mode: " + mode + ";\n"
        				+ "Internal mode: " + internalMode + ";\n"
        				+ "Target temperature: " + targetTemp + ";\n"
        				+ "Temperature from sensor: " + thermoTemp + ";\n"
        				+ "Temperature for information: " + tempForInfo + ";\n"
        				+ "Target fan speed: " + targetFanSpeed + ";\n"
        				+ "Fan speed: " + fanSpeed + ";\n"
        				+ "Time to end of mode switching: " + timeToEndOfSwitchingMode + ".\n"
        				+ "======================================");
            }
        }, 0, 1, TimeUnit.SECONDS);

        updated(properties);

        logger.info("Activating Air conditioner... Done.");
    }

    protected void deactivate(ComponentContext componentContext) {
        logger.info("Deactivating Air conditioner...");

        // shutting down the worker and cleaning up the properties
        this.worker.shutdown();

        logger.info("Deactivating Air conditioner... Done.");
    }

    public void updated(Map<String, Object> properties) {
        logger.info("Updated Air conditioner...");

        // store the properties received
        this.properties = properties;
        for (Entry<String, Object> property : properties.entrySet()) {
            logger.info("Update - {}: {}", property.getKey(), property.getValue());
        }
        
        if(!power.equals((String) this.properties.get(POWER))) {
        	power = (String) this.properties.get(POWER);
        	if(power.equals(POWER_ON))
        		timeToEndOfSwitchingMode = 60;
        }
        if(!mode.equals((String) this.properties.get(MODE))) {
        	mode = (String) this.properties.get(MODE);
        }
        targetTemp = (float) this.properties.get(TARGET_TEMPERATURE);
        tempForInfo = (String) this.properties.get(TEMP_FOR_INFO);
        targetFanSpeed = (String) this.properties.get(TARGET_FAN_SPEED);

        // try to kick off a new job
//        doUpdate(true);
        logger.info("Updated Air conditioner... Done.");
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
    public void onMessageArrived(KuraMessage message) {
    	logger.info("Message retrieved from queue!");
    	KuraPayload payload = message.getPayload();
    	if(payload.metricNames().contains("temperature")) {
    		thermoTemp = (float) payload.getMetric("temperature");
    	}
    }

    @Override
    public void onDisconnected() {
        // TODO Auto-generated method stub

    }

  
}
