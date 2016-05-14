package be.uantwerpen.sc.controllers;

/**
 * Created by Arthur on 9/05/2016.
 */

import be.uantwerpen.sc.services.DataService;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class mqttLocationPublisher {

    @Autowired
    private DataService dataService;

    public void publishLocation(Integer location){
        String content      = location.toString();
        int qos             = 2;
        String topic        = "BOT/" + dataService.getRobotID() + "/Location";
        String broker       = "tcp://146.175.139.66:1883";
        String clientId     = dataService.getRobotID().toString();
        MemoryPersistence persistence = new MemoryPersistence();

        if(dataService.getRobotID() != null) {
            try {
                MqttClient client = new MqttClient(broker, clientId, persistence);
                MqttConnectOptions connOpts = new MqttConnectOptions();
                connOpts.setCleanSession(true);
                connOpts.setUserName("arthur");
                connOpts.setPassword("arthur".toCharArray());
                //System.out.println("Connecting to broker: "+broker);
                client.connect(connOpts);
                //System.out.println("Connected");
                //System.out.println("Publishing message: " + content);
                MqttMessage message = new MqttMessage(content.getBytes());
                message.setQos(qos);
                client.publish(topic, message);
                //System.out.println("Message published");
                client.disconnect();
            } catch (MqttException me) {
                System.out.println("reason " + me.getReasonCode());
                System.out.println("msg " + me.getMessage());
                System.out.println("loc " + me.getLocalizedMessage());
                System.out.println("cause " + me.getCause());
                System.out.println("excep " + me);
                me.printStackTrace();
            }
        }
    }

    public void close(){
        try{

        }catch (Exception e){
            e.printStackTrace();
        }
        System.out.println("Disconnected");
    }
}