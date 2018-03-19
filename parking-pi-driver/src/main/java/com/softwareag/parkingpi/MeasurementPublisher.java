package com.softwareag.parkingpi;


import com.cumulocity.model.idtype.GId;
import com.cumulocity.model.measurement.MeasurementValue;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.rest.representation.measurement.MeasurementRepresentation;
import com.cumulocity.sdk.client.Platform;
import c8y.*;

import java.io.FileReader;
import java.math.BigDecimal;
import java.util.Date;

import com.pi4j.io.gpio.*;
import org.joda.time.DateTime;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeasurementPublisher {
    private static Logger logger = LoggerFactory.getLogger(MeasurementPublisher.class);
    public BigDecimal calc_Sensor(Integer trig, Integer echo) {
        BigDecimal distance =BigDecimal.ZERO;
        try {
            final GpioController gpio = GpioFactory.getInstance();
            logger.info("Into calc_Sensor");
            Pin trigPin=RaspiPin.getPinByAddress(trig);
            Pin echoPin=RaspiPin.getPinByAddress(echo);
            final GpioPinDigitalOutput sensorTriggerPin = gpio.provisionDigitalOutputPin(trigPin);
            final GpioPinDigitalInput sensorEchoPin = gpio.provisionDigitalInputPin(echoPin, PinPullResistance.PULL_DOWN);
            Thread.sleep(5000);
            sensorTriggerPin.high(); // Make trigger pin HIGH
            Thread.sleep((long) 0.01);// Delay for 10 microseconds
            sensorTriggerPin.low(); //Make trigger pin LOW

            while(sensorEchoPin.isLow()){ //Wait until the ECHO pin gets HIGH

            }
            long startTime= System.nanoTime(); // Store the surrent time to calculate ECHO pin HIGH time.
            while(sensorEchoPin.isHigh()){ //Wait until the ECHO pin gets LOW

            }
            long endTime= System.nanoTime(); // Store the echo pin HIGH end time to calculate ECHO pin HIGH time.
            distance= BigDecimal.valueOf(((((endTime-startTime)/1e3)/2) / 29.1));
            Thread.sleep(200);
            sensorTriggerPin.setShutdownOptions(true, PinState.LOW);
            sensorEchoPin.setShutdownOptions(true, PinState.LOW, PinPullResistance.OFF);
            gpio.shutdown();
            gpio.unprovisionPin(sensorTriggerPin);
            gpio.unprovisionPin(sensorEchoPin);
        } catch (InterruptedException e) {
            logger.error("could not read measurement from sensor", e);
        }
       
        return distance;
    }

    void publishMeasurement(Platform platform) {

        JSONParser parser=new JSONParser();
        Object obj=new Object();
        try {
            logger.info("reading the Json File Send measure.json");
            obj = parser.parse(new FileReader("/home/pi/Desktop/c8y/sendmeasure.json"));
        }catch (Exception e){
            logger.info("could not read sendmeasure.json");
        }
        JSONObject object=(JSONObject)obj;
        JSONArray sensors=(JSONArray)object.get("sensors");
                while(true){
                    try {
                        Thread.sleep(1000);
                        for (Object sensor : sensors) {
                            JSONObject sensorObj = (JSONObject) sensor;
                            String id = (String) sensorObj.get("id");
                            String trig = (String) sensorObj.get("trig");
                            String echo = (String) sensorObj.get("echo");
                            logger.info("Trying to publish for sensor " + id);
                            Integer trigpin = Integer.valueOf(trig);
                            Integer echopin = Integer.valueOf(echo);
                            MeasurementRepresentation sendMeasure = new MeasurementRepresentation();
                            ManagedObjectRepresentation sourceMo = platform.getInventoryApi().get(GId.asGId(id));

                            sendMeasure.setSource(sourceMo);
                            sendMeasure.setTime(new Date());
                            sendMeasure.setType("c8y_linux");
                            DistanceMeasurement disMeasure = new DistanceMeasurement();
                            MeasurementValue disValue = new MeasurementValue();
                            BigDecimal sensorVal = calc_Sensor(trigpin, echopin);
                            disValue.setValue(sensorVal);
                            logger.info("Sensor value of " + id + " is " + sensorVal);
                            disValue.setUnit("cm");
                            disMeasure.setDistance(disValue);
                            sendMeasure.set(disMeasure);
                            platform.getMeasurementApi().create(sendMeasure);
                            logger.info("measurement Sent");
                        }
                        Thread.sleep(1000);
                    }catch (Exception e){
                        e.printStackTrace();
                        logger.error("problem in Sending MeasureMent to c8y", e);
                    }
        }
    }
}