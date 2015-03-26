package com.zackehh.floodz.rmc;

import com.zackehh.floodz.common.Constants;
import com.zackehh.floodz.common.NameServiceHandler;
import corba.Alert;
import corba.RMCHelper;
import corba.RMCPOA;
import corba.SensorTuple;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class RMC extends RMCPOA {

    private static final Logger logger = LoggerFactory.getLogger(RMC.class);

    private final List<Alert> alerts = new ArrayList<>();

    public static void main(String[] args) {
        try {
            // Initialise the ORB
            ORB orb = ORB.init(args, null);

            // Retrieve a name service
            NamingContextExt nameService = NameServiceHandler.register(
                    orb, new RMC(), Constants.REGIONAL_MONITORING_CENTRE, RMCHelper.class
            );
            if(nameService == null){
                logger.error("Retrieved name service is null!");
                return;
            }

            // Server has loaded up correctly
            logger.info("Remote Monitoring Centre is operational.");

            // Wait for invocations from clients upon a new thread
            orb.run();
        } catch (Exception e) {
            logger.error("{}", e);
            e.printStackTrace(System.out);
        }
    }

    @Override
    public synchronized void cancelAlert(SensorTuple tuple) {

        int size = alerts.size();

        for(int i = 0; i < size; i++){

            Alert alert = alerts.get(i);

            if(alert.pair.equals(tuple)){
                alerts.remove(i);
                break;
            }

        }

        if(alerts.size() == size - 1){
            logger.info("Removed alert from sensor #{} in {}", tuple.sensor, tuple.zone);
        } else {
            logger.warn("Request to remove unknown alert from sensor #{} in {}", tuple.sensor, tuple.zone);
        }

    }

    @Override
    public synchronized void receiveAlert(Alert alert) {

        boolean stored = false;

        for(int i = 0, j = alerts.size(); i < j; i++){

            Alert storedAlert = alerts.get(i);

            if(storedAlert.pair.equals(alert.pair)){
                alerts.set(i, alert);
                stored = true;
                break;
            }

        }

        if(!stored){
            alerts.add(alert);
        }

        logger.info("Received alert from sensor #{} in {}", alert.pair.sensor, alert.pair.zone);
    }

    @Override
    public boolean testConnection(String name) {
        logger.info("Successfully received connection from LMS `{}`", name);
        return true;
    }

    @Override
    public Alert[] getAlerts(){
        return alerts.toArray(new Alert[alerts.size()]);
    }
}
