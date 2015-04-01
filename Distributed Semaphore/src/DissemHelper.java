import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;
/*
 * This is user helper process that will take care of all core operation responsibility for semaphore.
 * After receiving request from user process it will send request message to semaphore server,
 * that will broadcast. After receiving message from server it will store those message in queue
 * and will wait for acks. Once it received all acks then it will performs that particular operation
 * on semaphore. If that operation request is from its user process then it will acknowledge to its 
 * user.
 */
//Message that will be broadcast among all process
//Message that will be broadcast among all process
/*
 * This is message structure that will be used to communicate with 
 * server process and helper process.
 */
class Message {
	public static enum eMessageType {
		//message type.
		NONE("0"), POP("1"), VOP("2"), ACK("3"), REQP("4"), REQV("5");
			private String strValue;
			eMessageType(String val) {
				strValue = val;
			}
			@Override
			public String toString() {
				return strValue;
			}
			public static eMessageType fromString(String value) {
				for(eMessageType type : eMessageType.values()) {
					if(value.equalsIgnoreCase(type.toString())) {
						return type;
					}
				}
				return null;
			}
		}
	//message type
	private eMessageType msgType;
	//message originator id
	private String senderId;
	//target id for which acknowledgement is sent.
	private String targetId;
	//local time stamp
	private int timeStamp;
	//total number of user process involved 
	//in this system
	private int totalClient;
	public Message() {
		msgType = eMessageType.NONE;
		senderId = "";
		targetId = "";
		timeStamp = 0;
		totalClient = 0;
	}
	public eMessageType getMessageType() {return msgType;}
	public String getSenderId() {return senderId;}
	public String getTargetId() {return targetId;}
	public int getTimeStamp() {return timeStamp;}
	public int getTotalNode() {return totalClient;}
	
	public void setMessageType(eMessageType val) {msgType = val;}
	public void setSenderId(String val) {senderId = val;}
	public void setTargetId(String val) {targetId = val;}
	public void setTimeStamp(int val) {timeStamp = val;}
	public void setTotalNode(int val) {totalClient = val;}
	@Override
	public String toString() {
		return msgType.toString()  + ":" + senderId + ":" + targetId +":"+ Integer.toString(timeStamp)
			   + ":" + Integer.toString(totalClient);
	}

}

/*
 * Thread will be responsible to listen from server process.
 * After receiving message on socket it will parse the data and construct
 * appropriate message for main receiveMsg function.
 */
class ClientThread implements Runnable {
	
	private Socket clientSocket;
	private BufferedReader br = null;
	private DissemHelper helper = null;
	public ClientThread(Socket socket, DissemHelper helperObj) {
		clientSocket = socket;
		try {
			br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		helper = helperObj;
	}
	@Override
	public void run() {
		//continuous listening for server messsage.
		while(true) {
			try {
				String line = br.readLine();
				//receive data and parse the data and construct 
				//appropriate messsage
				Message msg = new Message();
				String[] msgParts = line.split(":");
				msg.setMessageType(Message.eMessageType.fromString(msgParts[0]));
				msg.setSenderId(msgParts[1]);
				msg.setTargetId(msgParts[2]);
				msg.setTimeStamp(Integer.parseInt(msgParts[3]));
				msg.setTotalNode(Integer.parseInt(msgParts[4]));
				//pass this message to receive function
				helper.receiveMsg(msg);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}		
	}	
}
/*
 * MessageRef class will have Message and counter that 
 * will be used to store total number of acknowledgement received
 * so far.
 */
class MessageRef {
	private Message msg;
	private int counter;
	public MessageRef(Message message) {
		msg = message;
		counter = 0;
	}
	public int getCounter() {return counter;}
	public Message getMessage() {return msg;}
	public void setCounter(int val) {counter = val;}
	public void setMessage(Message val) {msg = val;}
	@Override
	public String toString() {
		return msg.toString() + Integer.toString(counter);
	}
}
/*
 * This thread will be responsible to receive request from user process.
 */
class UserThread implements Runnable {
	private Socket socket = null;
	private DissemHelper userHelper = null;
	private BufferedReader br = null;
	public UserThread(Socket userSocket, DissemHelper helper) {
		socket = userSocket;
		userHelper = helper;
		try {
			br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	@Override
	public void run() {
		//continuously listen for request from user process
		//and based on request invoke appropriate method.
		while(true) {
			try {
				String str = br.readLine();
				if(str.equalsIgnoreCase("P")) {
					userHelper.requestP();
				} else if(str.equalsIgnoreCase("V")){
					userHelper.requestV();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}	
}
/*
 * This is user helper class that will be responsible to initiate all connection 
 * with server and user process and to maintain semaphore variable.
 * This class will also maintain message queue for refrence.
 */
public class DissemHelper {
	private Socket clientSocket = null;
	private String serverIP = "127.0.0.1";
	private int serverPort  = 4567;
	private Queue<MessageRef> messageQueue = null;
	private PrintStream pr = null;
	private int lc = 0; //local clock counter
	private String senderId = "";
	private int semaphoreVar = 5; //initial value
	private ServerSocket helperSocket = null;
	private Socket userSocket = null;
	private PrintStream userPR = null;
	private int helperPort = 0;
	public static void main(String [] args) {
		if(args.length != 4) {
			System.out.println("Usage: <exename> <sender-id> <serverIP <serverPort> <userServerPort>");
			return;
		}
		DissemHelper helperObj = new DissemHelper(args);
		//initialize all connection and other objects
		helperObj.Init();
	}
	public DissemHelper(String[] args) {
		senderId = args[0];
		serverIP = args[1];
		serverPort = Integer.parseInt(args[2]);
		helperPort = Integer.parseInt(args[3]);
	}
	public void Init() {
		try {
			//create client socket and connect to server 
			clientSocket = new Socket(serverIP, serverPort);
			pr = new PrintStream(clientSocket.getOutputStream());
			//now it will wait for user process to connect with
			System.out.println("User helper is waiting for user...");
			//create server socket for user process and wait for connection
			helperSocket = new ServerSocket(helperPort);
			userSocket = helperSocket.accept();
			System.out.println("User is connected with its helper...");
			userPR = new PrintStream(userSocket.getOutputStream());
			//after getting connected with user process listen in user thread 
			//for any request.
			Thread userThread = new Thread(new UserThread(userSocket, this));
			userThread.start();
			//create Message Ref Queue
			messageQueue = new LinkedList<MessageRef>();
			semaphoreVar = 5;
			//wait in client thread to listen for message from server
			Thread t = new Thread(new ClientThread(clientSocket, this));
			t.start();
			System.out.println("Client " + senderId + " is connected to server...");
		} catch(IOException e) {
			System.out.println("Couldn't connect to server socket with port "+ serverPort  +" and IP is " + serverIP);
		}
	}
	//requestP method that will initiate request 
	//for P operation to server.
	public void requestP() {
		//construct message, put appropriate type and sender id
		Message msg = new Message();
		msg.setMessageType(Message.eMessageType.POP);
		msg.setSenderId(senderId);
		msg.setTimeStamp(lc++);
		//and write message to server socket
		pr.println(msg.toString());
		pr.flush();
		System.out.println("Client: " + senderId + " send message " + msg.toString());
	}
	//requestV method that will initiate request 
	//for V operation to server.
	public void requestV() {
		//construct message, put appropriate type and sender id
		Message msg = new Message();
		msg.setMessageType(Message.eMessageType.VOP);
		msg.setSenderId(senderId);
		msg.setTimeStamp(lc++);
		//and write message to server socket
		pr.println(msg.toString());
		pr.flush();
		System.out.println("Client: " + senderId + " send message " + msg.toString());
	}
	//send acknowledgment for any request message received from server.
	public void sendAck(String target) {
		Message msg = new Message();
		//put appropriate type
		msg.setMessageType(Message.eMessageType.ACK);
		//put sender id
		msg.setSenderId(senderId);
		//put target id that will be request message
		//originator id for which ack is being sent.
		msg.setTargetId(target);
		msg.setTimeStamp(lc++);
		//write to socket.
		pr.println(msg.toString());
		pr.flush();
		System.out.println("Client: " + senderId + " send message " + msg.toString());
	}
	//This method will check message queue and retrive all message those
	//are acknowledged.
	private LinkedList<MessageRef> getMessage(Message msg) {
		LinkedList<MessageRef> listMessage = new LinkedList<MessageRef>();
		for(MessageRef m : messageQueue) {
			if(m.getCounter() == m.getMessage().getTotalNode()) {
				listMessage.add(m);
			}
		}
		return listMessage;
	}
	//record the ack message. It will increase counter for message
	//for which ack is intended for.
	private void recordAck(Message msg) {
		
		for(MessageRef mRef : messageQueue) {
			if(mRef.getMessage().getSenderId().equalsIgnoreCase(msg.getTargetId())) {
				mRef.setCounter(mRef.getCounter() + 1);
				System.out.println("MessageRef updated: " + mRef.toString());
			}
		}
	}
	//This method will be invoked after receiving message from server.
	public void receiveMsg(Message msg) {
		System.out.println("Client: " + senderId + " received message " + msg.toString());
		//if its P/V request message then add message to message queue and 
		//send ack correspoding to that message.
		if(msg.getMessageType() == Message.eMessageType.POP
				||msg.getMessageType() == Message.eMessageType.VOP) {
			//Insert Message into list
			messageQueue.add(new MessageRef(msg));
			sendAck(msg.getSenderId());
		} else if(msg.getMessageType() == Message.eMessageType.ACK) {
			//if its ack message then record the ack and take action on 
			//acknowledged message.
			//record the ack
			recordAck(msg);
			//get list of message those are fully acknowledged
			LinkedList<MessageRef> listMessage = getMessage(msg);
			for(MessageRef m : listMessage) {
				//if its p message then decreased semaphore value
				if(m.getMessage().getMessageType() == Message.eMessageType.POP) {
					semaphoreVar = semaphoreVar - 1;
					System.out.println("Semaphore value is decreased to " + semaphoreVar);
					//if its originator is this helper process then acknowledge user process also
					if(m.getMessage().getSenderId().equalsIgnoreCase(senderId)) {
						userPR.println("Done");
						userPR.flush();
					}
				} else if(m.getMessage().getMessageType() == Message.eMessageType.VOP){
					//if its V message then increased semaphore value
					semaphoreVar = semaphoreVar + 1;
					System.out.println("Semaphore value is increased to " + semaphoreVar);
					//if its originator is this helper process then acknowledge user process also
					if(m.getMessage().getSenderId().equalsIgnoreCase(senderId)) {
						userPR.println("Done");
						userPR.flush();
					}
				}
				messageQueue.remove(m);
			}
			
		}
	}
}
