/**
 * Aircraft
 * Contact: Swee Balachandran (swee.balachandran@nianet.org)
 * 
 * 
 * Copyright (c) 2011-2016 United States Government as represented by
 * the National Aeronautics and Space Administration.  No copyright
 * is claimed in the United States under Title 17, U.S.Code. All Other
 * Rights Reserved.
 *
 * Notices:
 *  Copyright 2016 United States Government as represented by the Administrator of the National Aeronautics and Space Administration. 
 *  All rights reserved.
 *     
 * Disclaimers:
 *  No Warranty: THE SUBJECT SOFTWARE IS PROVIDED "AS IS" WITHOUT ANY WARRANTY OF ANY KIND, EITHER EXPRESSED, 
 *  IMPLIED, OR STATUTORY, INCLUDING, BUT NOT LIMITED TO, ANY WARRANTY THAT THE SUBJECT SOFTWARE WILL CONFORM TO SPECIFICATIONS, ANY
 *  IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, OR FREEDOM FROM INFRINGEMENT, 
 *  ANY WARRANTY THAT THE SUBJECT SOFTWARE WILL BE ERROR FREE, OR ANY WARRANTY THAT DOCUMENTATION, IF PROVIDED, 
 *  WILL CONFORM TO THE SUBJECT SOFTWARE. THIS AGREEMENT DOES NOT, IN ANY MANNER, CONSTITUTE AN ENDORSEMENT BY GOVERNMENT 
 *  AGENCY OR ANY PRIOR RECIPIENT OF ANY RESULTS, RESULTING DESIGNS, HARDWARE, SOFTWARE PRODUCTS OR ANY OTHER APPLICATIONS 
 *  RESULTING FROM USE OF THE SUBJECT SOFTWARE.  FURTHER, GOVERNMENT AGENCY DISCLAIMS ALL WARRANTIES AND 
 *  LIABILITIES REGARDING THIRD-PARTY SOFTWARE, IF PRESENT IN THE ORIGINAL SOFTWARE, AND DISTRIBUTES IT "AS IS."
 *
 * Waiver and Indemnity:  
 *   RECIPIENT AGREES TO WAIVE ANY AND ALL CLAIMS AGAINST THE UNITED STATES GOVERNMENT, 
 *   ITS CONTRACTORS AND SUBCONTRACTORS, AS WELL AS ANY PRIOR RECIPIENT.  IF RECIPIENT'S USE OF THE SUBJECT SOFTWARE 
 *   RESULTS IN ANY LIABILITIES, DEMANDS, DAMAGES,
 *   EXPENSES OR LOSSES ARISING FROM SUCH USE, INCLUDING ANY DAMAGES FROM PRODUCTS BASED ON, OR RESULTING FROM, 
 *   RECIPIENT'S USE OF THE SUBJECT SOFTWARE, RECIPIENT SHALL INDEMNIFY AND HOLD HARMLESS THE UNITED STATES GOVERNMENT, 
 *   ITS CONTRACTORS AND SUBCONTRACTORS, AS WELL AS ANY PRIOR RECIPIENT, TO THE EXTENT PERMITTED BY LAW.  
 *   RECIPIENT'S SOLE REMEDY FOR ANY SUCH MATTER SHALL BE THE IMMEDIATE, UNILATERAL TERMINATION OF THIS AGREEMENT.
 */
package gov.nasa.larcfm.ICAROUS;
import com.MAVLink.enums.*;
import com.MAVLink.common.*;
import com.MAVLink.icarous.*;
import gov.nasa.larcfm.Util.Plan;
import gov.nasa.larcfm.Util.Position;
import gov.nasa.larcfm.Util.ErrorLog;
import gov.nasa.larcfm.Util.ErrorReporter;
import gov.nasa.larcfm.Util.ParameterData;
import java.util.*;

public class Aircraft implements ErrorReporter{

    public enum AP_MODE{
	ND,AUTO,GUIDED
    }

    public enum FLIGHT_STATE{
	IDLE, TAKEOFF, TAKEOFF_CLIMB, CRUISE, LAND, TERMINATE
    }
    
    public Interface apIntf;
    public Interface comIntf;

    public AircraftData FlightData;
    public FSAM fsam;
    public Mission mission;
    int MissionState;
    
    public FLIGHT_STATE state; // State machine state for Flight functionalities
    public AP_MODE apMode;    // Current AP mode

    
    public long timeStart;
    public long timeCurrent;
    public String timeLog;
    
    public ErrorLog error;

    private boolean landStart;
    public ParameterData pData;
    public boolean IcarousReset;

    private ArrayList<msg_command_ack> CmdError;
    
    public Aircraft(Interface ap_Intf,Interface com_Intf,AircraftData acData,Mission mc,ParameterData pdata){

	pData            = pdata;
	apIntf           = ap_Intf;
	comIntf          = com_Intf;
	FlightData       = acData;
	mission          = mc;
	state            = FLIGHT_STATE.IDLE;
	apMode           = AP_MODE.ND;
	fsam             = new FSAM(this,mission);
	error            = new ErrorLog("Aircraft");
	timeStart        = System.nanoTime();
	timeLog          = String.format("%.3f",0.0f);
	landStart        = false;
	MissionState     = 0;
	CmdError         = new ArrayList<msg_command_ack>();
	
    }

    public void Reset(){
	state = FLIGHT_STATE.IDLE;
	apMode = AP_MODE.ND;
	landStart = false;
	FlightData.Reset();
    }

    // Function to send commands to pixhawk
    public int SendCommand( int target_system,
			    int target_component,
			    int command,
			    int confirmation,
			    float param1,
			    float param2,
			    float param3,
			    float param4,
			    float param5,
			    float param6,
			    float param7,
			    boolean CheckACK){

	msg_command_long CommandLong  = new msg_command_long();
	
	CommandLong.target_system     = (short) target_system;
	CommandLong.target_component  = (short) target_component;
	CommandLong.command           = command;
	CommandLong.confirmation      = (short) confirmation;
	CommandLong.param1            = param1;
	CommandLong.param2            = param2;
	CommandLong.param3            = param3;
	CommandLong.param4            = param4;
	CommandLong.param5            = param5;
	CommandLong.param6            = param6;
	CommandLong.param7            = param7;
	
	apIntf.Write(CommandLong);

	try{
	    Thread.sleep(100);
	}catch(InterruptedException e){
	    System.out.println(e);
	}

	if(CheckACK){
	    return CheckAcknowledgement(command);
	}
	else{
	    return 1;
	}
    }

    // Yaw command
    public void SetYaw(double heading){

	SendCommand(0,0,MAV_CMD.MAV_CMD_CONDITION_YAW,0,
		    (float)heading,0,1,0,
		    0,0,0,false);
	
    }

    // Position command
    public int SetGPSPos(double lat,double lon, double alt){

	msg_set_position_target_global_int msg= new msg_set_position_target_global_int();

	msg.time_boot_ms     = 0;
	msg.target_system    = 0;
	msg.target_component = 0;
	msg.coordinate_frame = MAV_FRAME.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT;
	msg.type_mask        = 0b0000111111111000;
	msg.lat_int          = (int) (lat*1E7);
	msg.lon_int          = (int) (lon*1E7);
	msg.alt              = (float) alt;
	msg.vx               = 0;
	msg.vy               = 0;
	msg.vz               = 0;
	msg.afx              = 0;
	msg.afy              = 0;
	msg.afz              = 0;
	msg.yaw              = 0;
	msg.yaw_rate         = 0;

	apIntf.Write(msg);

	try{
	    Thread.sleep(100);
	}catch(InterruptedException e){
	    System.out.println(e);
	}
		
	//return CheckAcknowledgement();
	return 1;
    }

    // Velocity command
    public int SetVelocity(double Vn,double Ve, double Vu){

	msg_set_position_target_local_ned msg= new msg_set_position_target_local_ned();

	msg.time_boot_ms     = 0;
	msg.target_system    = 0;
	msg.target_component = 0;
	msg.coordinate_frame = MAV_FRAME.MAV_FRAME_LOCAL_NED;	
	msg.type_mask        = 0b0000111111000111;
	msg.x                = 0;
	msg.y                = 0;
	msg.z                = 0;
	msg.vx               = (float)Vn;
	msg.vy               = (float)Ve;
	msg.vz               = (float)Vu;
	msg.afx              = 0;
	msg.afy              = 0;
	msg.afz              = 0;
	msg.yaw              = 0;
	msg.yaw_rate         = 0;

	apIntf.Write(msg);

	try{
	    Thread.sleep(100);
	}
	catch(InterruptedException e){
	    System.out.println(e);
	}
	
	return 1;
    }

    // Function to set mode
    public int SetMode(int modeid){

	msg_set_mode Mode = new msg_set_mode();
	Mode.target_system = (short) 0;
	Mode.base_mode     = (short) 1;
	Mode.custom_mode   = (long) modeid;

	apIntf.Write(Mode);

	try{
	    Thread.sleep(200);
	}catch(InterruptedException e){
	    System.out.println(e);
	}
	
	//return CheckAcknowledgement();
	return 1;
    }

    // Check acknowledgement
    public int CheckAcknowledgement(int command){

	short status;

	msg_command_ack msgCommandAck = null;

	//System.out.println("Checking for ack for:"+command);
	while(msgCommandAck == null){	    
	    msgCommandAck = FlightData.Inbox.GetCommandAck(1);
	    if(msgCommandAck != null){
		//System.out.format("Got ack for %d = %d\n",msgCommandAck.command,msgCommandAck.result);
	    }
	    if( (msgCommandAck != null) && (msgCommandAck.command != command)){
		msgCommandAck = null;
	    }
	}

	CmdError.add(msgCommandAck);

	status = msgCommandAck.result;
	
	switch(status){

	case 0:
	    //error.addWarning("Command Accepted");
	    return 1;
	    
	case MAV_CMD_ACK.MAV_CMD_ACK_OK:
	    //error.addError("Command Accepted");	    
	    return 1;
	    
	case MAV_CMD_ACK.MAV_CMD_ACK_ERR_FAIL:
	    error.addError("Command error: fail");	    
	    return 0;
	    
	case MAV_CMD_ACK.MAV_CMD_ACK_ERR_ACCESS_DENIED:
	    error.addError("Command error: access denied");
	    return 0;
	    
	case MAV_CMD_ACK.MAV_CMD_ACK_ERR_NOT_SUPPORTED:
	    error.addError("Command error: not supported");
	    return 0;

	case MAV_CMD_ACK.MAV_CMD_ACK_ERR_COORDINATE_FRAME_NOT_SUPPORTED:
	    error.addError("Command error: frame not supported");
	    return 0;

	case MAV_CMD_ACK.MAV_CMD_ACK_ERR_COORDINATES_OUT_OF_RANGE:
	    error.addError("Command error: coordinates out of range");
	    return 0;

	case MAV_CMD_ACK.MAV_CMD_ACK_ERR_X_LAT_OUT_OF_RANGE:
	    error.addError("Command error: x lat out of range");
	    return 0;

	case MAV_CMD_ACK.MAV_CMD_ACK_ERR_Y_LON_OUT_OF_RANGE:
	    error.addError("Command error: y lat out of range");
	    return 0;

	case MAV_CMD_ACK.MAV_CMD_ACK_ERR_Z_ALT_OUT_OF_RANGE:
	    error.addError("Command error: z alt out of range");
	    return 0;

	case MAV_CMD_ACK.MAV_CMD_ACK_ENUM_END:
	    error.addError("Command error: enum end");
	    return 0;

	default:
	    error.addError("Unrecognized result");
	    return -1;
		
	}	   	    		
    }

    public synchronized msg_command_ack GetCmdError(){
	if(CmdError.size() > 0){
	    return (msg_command_ack)CmdError.remove(0);
	}
	else{
	    return null;
	}
    }
    
    public void EnableDataStream(int option){
	
	msg_heartbeat msg1 = new msg_heartbeat();
	
	msg1.type      = MAV_TYPE.MAV_TYPE_ONBOARD_CONTROLLER;
	msg1.autopilot = MAV_AUTOPILOT.MAV_AUTOPILOT_GENERIC;
	
	apIntf.Write(msg1);

	msg_request_data_stream req = new msg_request_data_stream();
	req.req_message_rate = 10;
	req.req_stream_id    = MAV_DATA_STREAM.MAV_DATA_STREAM_ALL;           ;
	req.start_stop       = (byte) option;
	req.target_system    = 0;
	req.target_component = 0;

	apIntf.Write(req);

	
    }

    public void SetSpeed(float speed){
	
	SendCommand(0,0,MAV_CMD.MAV_CMD_DO_CHANGE_SPEED,0,
		    1,speed,0,0,0,0,0,false);

    }
    
    public int PreFlight(){
	
	// Send flight plan to pixhawk
	System.out.println("PREFLIGHT");
	//EnableDataStream(0);
	//synchronized(apIntf){
	//System.out.println("Starting mission");	    
	//FlightData.SendFlightPlanToAP(apIntf);
	//EnableDataStream(1);
	//}
	
	return 1;
    }

    public int Flight(){

	float targetAlt        = (float)pData.getValue("TAKEOFF_ALT");
	Position currPosition  = FlightData.acState.positionLast();
		
	timeLog                = String.format("%.3f",(double) (timeCurrent - timeStart)/1E9);
	FSAM_OUTPUT status;
	
	switch(state){

	case TAKEOFF:

	    int ack;
	    
	    // Set mode to guided
	    error.addWarning("[" + timeLog + "] MODE:GUIDED");
	    SetMode(4);
	    apMode = AP_MODE.GUIDED;
	    
	    // Arm the throttles
	    error.addWarning("[" + timeLog + "] CMD:ARM");
	    ack = SendCommand(0,0,MAV_CMD.MAV_CMD_COMPONENT_ARM_DISARM,0,
			      1,0,0,0,0,0,0,true);
	    
	    // Send takeoff command only if 
	    
	    error.addWarning("[" + timeLog + "] CMD:TAKEOFF");
	    // Takeoff at current location
	    error.addWarning("[ "+ timeLog + " ] ALT: "+targetAlt);
	    ack = SendCommand(0,0,MAV_CMD.MAV_CMD_NAV_TAKEOFF,0,
			      1,0,0,0, (float) currPosition.latitude(),
			      (float) currPosition.longitude(),
			      targetAlt,true);
	    
	    
	    if(ack == 0){
		return -1;
	    }
	    else{
		state = FLIGHT_STATE.TAKEOFF_CLIMB;
	    }
	    
	    break;

	case TAKEOFF_CLIMB:

	    // Switch to auto once targetAlt [m] is reached and start mission in auto mode
	    if( Math.abs(currPosition.alt() - targetAlt) < 0.5 ){
		error.addWarning("[" + timeLog + "] MODE:AUTO");
		state = FLIGHT_STATE.CRUISE;
		SetMode(3);
		apMode = AP_MODE.AUTO;
		
		// Set speed
		FlightData.FP_nextWaypoint++;
		float speed = GetSpeed();			        
		SetSpeed(speed);
		error.addWarning("[" + timeLog + "] CMD:SPEED CHANGE TO "+speed+" m/s");

	    }
	    

	    break;

	case CRUISE:
	    	    
	    // Check for conflicts and determine mode
	    status = fsam.Monitor();

	    if(status == FSAM_OUTPUT.CONFLICT){
		fsam.Resolve();
	    }
	    else if(status == FSAM_OUTPUT.NOOP){
		mission.Execute(this);
	    }

	    // Check if next mission item (waypoint) has been reached
	    if(CheckMissionItemReached()){
		Plan CurrentFlightPlan = FlightData.CurrentFlightPlan;
		error.addWarning("[" + timeLog + "] MSG: Reached waypoint");
		FlightData.FP_nextWaypoint++;
		
		if(FlightData.FP_nextWaypoint < FlightData.FP_numWaypoints){
		    float speed = GetSpeed();					
		    SetSpeed(speed);
		    error.addWarning("[" + timeLog + "] CMD:SPEED CHANGE TO "+speed+" m/s");
		}
	    }
	    
	    if((FlightData.FP_nextWaypoint >= FlightData.FP_numWaypoints)){
		state = FLIGHT_STATE.LAND;
	    }
		
	    break;

	case LAND:

	    if(!landStart){
		// Set mode to guided
		error.addWarning("[" + timeLog + "] MSG: Landing started");
		error.addWarning("[" + timeLog + "] MODE:GUIDED");
		SetMode(4);
		apMode = AP_MODE.GUIDED;
	    
		SendCommand(0,0,MAV_CMD.MAV_CMD_NAV_LAND,0,
			    6.0f,0,0,0,
			    (float) currPosition.latitude(),
			    (float) currPosition.longitude(),
			    (float) currPosition.alt(),false);

		landStart = true;
	    
	    }

	    if(currPosition.alt() < 6.0){
		state =FLIGHT_STATE.TERMINATE;
		
	    }
	    
	    	    
	    break;

	case TERMINATE:
	    return 1;
	
	}// end switch

	return 0;
	
	
    }//end Flight()

    public synchronized int GetAircraftState(){
	switch(state){

	case IDLE:
	    return 0;
	    
	case TAKEOFF:
	    return 1;

	case TAKEOFF_CLIMB:
	    return 2;
	    
	case CRUISE:
	    return 3;
	    
	case LAND:
	    return 4;

	default:
	    return -1;
	}
    }

    public float GetSpeed(){

	Plan CurrentFlightPlan = FlightData.CurrentFlightPlan;
	
	float speed = (float)(CurrentFlightPlan.pathDistance(FlightData.FP_nextWaypoint-1,true)/
			      (CurrentFlightPlan.getTime(FlightData.FP_nextWaypoint) -
			       CurrentFlightPlan.getTime(FlightData.FP_nextWaypoint-1)));

	return speed;
    }

    public boolean CheckMissionItemReached(){

	boolean reached = false;

	msg_mission_item_reached msgMissionItemReached = FlightData.Inbox.GetMissionItemReached();
	if(msgMissionItemReached != null){
	    reached = true;
	}


	return reached;	
    }        
                
    public int Terminate(){

	return 1;
    }

    public boolean hasError() {
	return error.hasError();
    }
    
    public boolean hasMessage() {
	return error.hasMessage();
    }
    
    public String getMessage() {
	return error.getMessage();
    }
    
    public String getMessageNoClear() {
	return error.getMessageNoClear();
    }

    

}
