import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;

/*
 * This is user process that will be responsible to initiate any P/V operation
 * on semaphore. User process will communicate to its helper process via socket communication.
 * User process can send any request of P/V operation then it will wait till helper process 
 * acknowledge it about the operation success.
 */

/*
 * This is user thread, that will continuously listen for User helper process message
 * on its socket. After getting any Done message it will notify on its thread object.
 * So any waiting after operation will be notified and control will proceed further.
 */
class UserThread extends Thread {
	//user socket
	private Socket socket = null;
	private BufferedReader br = null;
	public UserThread(Socket userSocket) {
		socket = userSocket;
		try {
			//get input reader on socket
			br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	//This is main thread loop.
	@Override
	public void run() {	
			try {
				//It will continously listen for userhelper process message
				while(true) {
					String str = br.readLine();
					synchronized(this) {
						//if its Done message then notify on thread object
						if(str.equalsIgnoreCase("Done")) {
							notify();
						}
					}
				}			
				
			} catch (IOException e) {
				e.printStackTrace();
			}	
	}
	
}

public class DisSem {
	private Socket userSocket = null;
	private String helperIP = "";
	private int helperPort = 0;
	private PrintStream pr = null;
	private UserThread t = null;
	DisSem(String[] args) {
		helperIP = args[0];
		helperPort = Integer.parseInt(args[1]);
	}
	public static void main(String [] args) {
		if(args.length != 2) {
			System.out.println("Usage: <exename> <helperIP> <helperPort>");
			return;
		}
		DisSem user = new DisSem(args);
		//initialize user process and its socket.
		user.Init();
		//now wait for user input for operation request
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		while(true) {
			System.out.println("Please enter single character for operation\nP(P operation)\nV(V operation\nq(To quit))");
			try {
				String str = br.readLine();
				if(str.equalsIgnoreCase("P")) {
					user.requestP();
				} else if(str.equalsIgnoreCase("V")) {
					user.requestV();
				} else if(str.equalsIgnoreCase("q")) {
					System.exit(0);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	//Init the user process
	public void Init() {
		try {
			//create user socket and connect to helper process on is ip and port
			userSocket = new Socket(helperIP, helperPort);
			//get output stream of socket
			pr = new PrintStream(userSocket.getOutputStream());
			//create thread that will listen for message from helper process
			t = new UserThread(userSocket);
			t.start();
			System.out.println("Client is connected to helper Process...");
		} catch(IOException e) {
			System.out.println("Couldn't connect to server socket with port "+ helperPort  +" and IP is " + helperIP);
		}
	}
	//request V operation
	public void requestV() {
		synchronized(t) {
			System.out.println("Requesting for V operation");
			//put message for helper process
			pr.println("V");
			pr.flush();
			//and now wait, this wait will be notified by listening thread
			try {
				t.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println("V operation ack recieved and operation completed");
		}
		
	}
	//request P operation
	public void requestP() {
		synchronized (t) {
			System.out.println("Requesting for P operation");
			//put message for helper process
			pr.println("P");
			pr.flush();
			try {
				//and now wait, this wait will be notified by listening thread
				t.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println("P operation ack recieved and operation completed");
		}
		
	}
}
