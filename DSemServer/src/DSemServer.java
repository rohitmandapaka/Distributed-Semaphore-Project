import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.LinkedList;

/*
 * This is server process that will accept connection from user helper process.
 * It will receive message and broadcast to all userhelper those are already connected.
 */
//Message that will be broadcast among all process
class Message {
	public static enum eMessageType {
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
	private eMessageType msgType;
	private String senderId;
	private String targetId;
	private int timeStamp;
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
 * This thread will be responsible to communicate with individual clients.
 */
class ServerThread implements Runnable {
	private Socket clientSocket = null;
	private DSemServer mainServer = null;
	private BufferedReader br = null;
	
	public ServerThread(DSemServer server, Socket socket) {
		mainServer = server;
		clientSocket = socket;
		try {
			br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void run() {
		//It will continuously listen for message
		while(true) {
			try {
				String line = br.readLine();
				//after receiving message it will parse data
				//and create appropriate message.
				Message msg = new Message();
				String[] msgParts = line.split(":");
				msg.setMessageType(Message.eMessageType.fromString(msgParts[0]));
				msg.setSenderId(msgParts[1]);
				msg.setTargetId(msgParts[2]);
				//add server clock time stamp
				synchronized(DSemServer.lock) {
					msg.setTimeStamp(DSemServer.localClock++);
				}
				//put total number of clients
				msg.setTotalNode(mainServer.getNodeCount());
				//send message to broadcast
				mainServer.broadCastMessage(msg);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
}
/*
 * This is server class responsible to accept the connection and
 * broadcast message.
 */
public class DSemServer {
	public static int localClock = 0;
	public static Object lock = new Object();
	private ServerSocket socket = null;
	private LinkedList<Socket> clientSocketList = null;
	
	public static void main(String[] args) {
		if(args.length != 1) {
			System.out.println("Usage: exeName <portNum>");
			return;
		}
		DSemServer serverObj = new DSemServer();
		//initialize server
		serverObj.initServer(args);
	}
	
	public int getNodeCount() {
		return clientSocketList.size();
	}
	public void initServer(String[] args) {
		clientSocketList = new LinkedList<Socket>();
		try {
			//create server socket
			socket = new ServerSocket(Integer.parseInt(args[0]));
			System.out.println("DSemServer is listenin at port " + args[0]);
			while(true) {
				//accept connection
				Socket clientSocket = socket.accept();
				//add client socket to its list that will be used to broadcast message
				clientSocketList.add(clientSocket);
				//create corresponding thread for a client for communication purpose.
				Thread svThread = new Thread(new ServerThread(this, clientSocket));
				svThread.start();
			}
			
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	//Broad cast all message those were received.
	public void broadCastMessage(Message msg) {
		synchronized(this) {
			Iterator<Socket> iteSocket = clientSocketList.iterator();
			while(iteSocket.hasNext()) {
				Socket socket = iteSocket.next();
				try {
					//write on each socket
					PrintStream ps = new PrintStream(socket.getOutputStream());
					ps.println(msg.toString());
					ps.flush();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
}
