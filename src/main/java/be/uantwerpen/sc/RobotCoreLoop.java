package be.uantwerpen.sc;

import be.uantwerpen.sc.controllers.MapController;
import be.uantwerpen.sc.controllers.PathController;
import be.uantwerpen.sc.controllers.mqtt.MqttJobSubscriber;
import be.uantwerpen.sc.services.*;
import be.uantwerpen.sc.tools.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;

/**
 * Created by Arthur on 4/05/2016.
 */
@Service
public class RobotCoreLoop implements Runnable
{
    @Autowired
    private DataService dataService;

    @Autowired
    private PathplanningType pathplanningType;

    @Autowired
    private WorkingmodeType workingmodeType;

    @Autowired
    private MqttJobSubscriber jobSubscriber;

    @Autowired
    private JobService jobService;

    @Value("${sc.core.ip:localhost}")
    private String serverIP;

    @Value("#{new Integer(${sc.core.port}) ?: 1994}")
    private int serverPort;

    private Long botId = 1L;
    @Autowired
    private QueueService queueService;
    @Autowired
    private MapController mapController;
    @Autowired
    private PathController pathController;

    public IPathplanning pathplanning;

    private TerminalService terminalService;
    private Logger logger = LoggerFactory.getLogger(RobotCoreLoop.class);


    public RobotCoreLoop(){

    }

    @PostConstruct
    private void postconstruct(){ //struct die wordt opgeroepen na de initiele struct. Omdat de autowired pas wordt opgeroepen NA de initiele struct
        //Setup type
        Terminal.printTerminalInfo("Selected PathplanningType: " + pathplanningType.getType().name());
        Terminal.printTerminalInfo("Selected WorkingmodeType: " + workingmodeType.getType().name());
    }

    public void run() {
        //getRobotId
        terminalService=new TerminalService(); //terminal service starten. terminal wordt gebruikt om bepaalde dingen te printen en commandos in te geven
        RestTemplate restTemplate = new RestTemplate(); //standaard resttemplate gebruiken

        Long robotID = restTemplate.getForObject("http://" + serverIP + ":" + serverPort + "/bot/initiate/" + botId + "/" //aan de server laten weten dat er een nieuwe bot zich aanbied
                +workingmodeType.getType().toString(), Long.class); //Aan de server laten weten in welke mode de bot werkt

        dataService.setRobotID(robotID);
        jobService.setRobotCoreLoop(this);
        jobService.setEndJob(-1);
        jobService.removeCommands();

        if(!jobSubscriber.initialisation()) //subscribe to topics to listen to jobs
        {
            System.err.println("Could not initialise MQTT Job service!");
        }

        //Setup interface for correct mode of pathplanning
        setupInterface();
        logger.info("Interface is set up");
        //Wait for tag read
        //Read tag where bot is located
        synchronized (this) {
            while (dataService.getTag().trim().equals("NONE") || dataService.getTag().equals("NO_TAG")) {
                try {
                    //Read tag
                    queueService.insertJob("TAG READ UID");
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        logger.info("Tag: " + dataService.getTag());

       // updateStartLocation();

        //Request map at server with rest
        dataService.map = mapController.getMap();
        logger.info("Map received " + dataService.map.getNodeList());

        //Set location of bot
        Long locationID = dataService.map.getLocationByRFID(dataService.getTag());
        dataService.setCurrentLocation(locationID);
        logger.info("Start Location: " + dataService.getCurrentLocation()+"\n\n");

        //We have the map now, update link

        if(dataService.getWorkingmodeEnum()==WorkingmodeEnum.INDEPENDENT)
            dataService.firstLink();
        logger.info("link updated");
        logger.info("next: "+dataService.getNextNode());

        RestTemplate rest = new RestTemplate();
        rest.getForObject("http://" + serverIP + ":" + serverPort + "/bot/" + botId + "/locationUpdate/" +dataService.getCurrentLocation(), boolean.class);
        boolean response = false;
        while(!response) {
            response = rest.getForObject("http://" + serverIP + ":" + serverPort + "/point/requestlock/" +dataService.getRobotID()+ "/" + dataService.getCurrentLocation(), boolean.class);
            logger.info("Lock Requested : " + dataService.getCurrentLocation());
            if(!response) {
                logger.trace("First point lock denied with id : " + dataService.getCurrentLocation());
                try {
                    Thread.sleep(200);
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        while(!Thread.interrupted()){

            if(dataService.job != null){
                jobService.performJob(dataService.job);
                dataService.job = null;
            }
        }
    }

    public IPathplanning getPathplanning()
    {
        return this.pathplanning;
    }


    private void setupInterface(){
        switch (pathplanningType.getType()){
            case DIJKSTRA:
                pathplanning = new PathplanningService();
                dataService.setPathplanningEnum(PathplanningEnum.DIJKSTRA);
                break;
            case RANDOM:
                pathplanning = new RandomPathPlanning(pathController);
                dataService.setPathplanningEnum(PathplanningEnum.RANDOM);
                break;
            default:
                //Dijkstra
                pathplanning = new PathplanningService();
                dataService.setPathplanningEnum(PathplanningEnum.DIJKSTRA);
        }

        switch(workingmodeType.getType()) {
            case PARTIALSERVER:
                dataService.setworkingmodeEnum(WorkingmodeEnum.PARTIALSERVER);
                break;
            case FULLSERVER:
                dataService.setworkingmodeEnum(WorkingmodeEnum.FULLSERVER);
                break;
            case INDEPENDENT:
                dataService.setworkingmodeEnum(WorkingmodeEnum.INDEPENDENT);
                break;
            case PARTIALSERVERNG:
                dataService.setworkingmodeEnum(WorkingmodeEnum.PARTIALSERVERNG);
                break;
            default:
                dataService.setworkingmodeEnum(WorkingmodeEnum.INDEPENDENT);
        }
    }
}
