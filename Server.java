import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.HashMap;
import java.util.Arrays;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.SocketException;
import java.io.*;
import java.lang.Runnable;

public class Server {
	protected static int RESP_TIMEOUT = 5000;
	protected static String STUDENT_ID = "ed9d356567882c76a5e1ab4e224a3c50d80fb962838dc52afd5e5e20a7f5817e";
	
	public abstract class Response {
		public abstract void response(Socket sock, InputStream in, OutputStream out) throws IOException;
	}

	private Response killServerResponder = new Response() { //!!! non-static
		public void response(Socket sock, InputStream in, OutputStream out) throws IOException {
			System.out.println("KILL_SERVER from " + sock);
			Server.this.shutdown = true; 
		}
	};

	private Response heloServerResponder = new Response() { //also non-static
		public void response(Socket sock, InputStream in, OutputStream out) throws IOException {
			System.out.println("HELO from " + sock);
			//read message
			int b = 0;
			out.write("HELO ".getBytes());
			while (b != (int) '\n') {
				b = in.read();
				out.write(b);
			}

			//now write the ip-port-studentid
			PrintStream outStr = new PrintStream(out); //autoflush OFF
			String serverIp = listenSock.getInetAddress().getHostAddress();
			int port = listenSock.getLocalPort();
			outStr.printf("IP:%s\nPort:%d\nStudentID:%s\n", serverIp, port, STUDENT_ID);
			outStr.flush(); //all at once
			//outStr.close();
		}
	};

	private class Responder implements Runnable {
		private Socket sock = null;
		private InputStream in;
		private OutputStream out;

		public Responder(Socket s) {
			sock = s;
		}

		public void run() {
			System.out.println("strted thread with socket " + sock.toString());
			try {
				sock.setSoTimeout(Server.RESP_TIMEOUT);
				in = sock.getInputStream();
				out = sock.getOutputStream();

				while (true) {
					try {
						if (Server.this.respond(sock, in, out)) {
							System.out.println("successful handling of " + sock);
						}
						else {
							System.out.println("bad command from " + sock);
							break;
						}
						out.flush();
					}
					catch(SocketTimeoutException e) {
						System.out.println("socket " + sock.toString() + " timeout");
						e.printStackTrace();
						break;
					}
				}

				//in.close();
				//out.close();
				//sock.close();
				//System.out.println("socket " + sock.toString() + " thread ended gracefully");
			}
			catch(SocketTimeoutException e) {
				System.out.println("socket " + sock.toString() + " timeout");
				e.printStackTrace();
			}
			catch(SocketException e) {
				System.out.println("socket " + sock.toString() + " error " + e.toString());
				e.printStackTrace();
			}
			catch(IOException e) {
				System.out.println("socket " + sock.toString() + " io error " + e.toString());
			}
		}
	}

	//edit only before any responder threads are created
	private HashMap<String, Response> commandTable = new HashMap<String, Response>();
	private CommandTrie commandTrie = new CommandTrie();
	
	private ExecutorService pool = null;
	private ServerSocket listenSock = null;
	protected boolean shutdown = false; //write-only from workers

	public Server() {
		//add standard handlers
		addResponse("KILL_SERVER\n", killServerResponder);
		addResponse("HELO ", heloServerResponder);
	}

	//do not call this once threads have been started
	protected final void addResponse(String command, Response resp) {
		//O(1) insert
		boolean good = commandTrie.addRule(command.getBytes());
		if (good) {
			commandTable.put(command, resp);
		}
		else {
			System.out.printf("ambiguous command prefix \"%s\"\n", command);
		}
	}

	//this is accessed by the responder threads
	//it must only read the Server fields
	//it must be thread safe
	protected boolean respond(Socket sock, InputStream in, OutputStream out) throws IOException {
		//read data until all known commands have been tested.
		//stop at the first match
	
		CommandTrie.TrieMatcher match = commandTrie.matcher();
		while (match.advance((byte)in.read());
		if (match.isDone()) {
			byte[] cmd = match.getMatched();
			String key = new String(cmd);
			Response resp = commandTable.get(key);
			assert(resp != null);
			
			//call callback
			System.out.println("got command " + key);
			resp.response(sock, in, out);
			return true;
		}
		else {
			//couldn't match any commands
			return false;
		}
	}

	public final void start(int port, int threads, int backlog) {
		try {
			pool = Executors.newFixedThreadPool(threads);
			listenSock = new ServerSocket(port, backlog);
			

			//make it possible to check the shutdown flag while waiting for connections
			listenSock.setSoTimeout(50);		

			while (true) {
				if (shutdown) {
					break;
				}
				
				try {
					Socket sock = listenSock.accept();
					System.out.println("connection from " + sock.getInetAddress().getCanonicalHostName());
					Runnable worker = new Responder(sock);
					pool.execute(worker);
				}
				catch (SocketTimeoutException e) {
					//pass
				}
			}
		
			pool.shutdown();
			while (!pool.isTerminated()) {
				try {
					Thread.sleep(5);
				}
				catch(Exception e) {
					//pass
				}
			}
		
			System.out.println("terminated all threads");
		}
		catch (Exception e) {
			System.out.println("exception " + e);
		}
	}

	public static void main(String[] args) {
		if (args.length >= 1 && args.length <= 3) {
			int port = Integer.parseInt(args[0], 10);
			int threads = Runtime.getRuntime().availableProcessors();
			int backlog = 50;
			if (args.length > 1) threads = Integer.parseInt(args[1], 10);
			if (args.length > 2) backlog = Integer.parseInt(args[2], 10);

			System.out.println("listening on port " + port);
			Server s = new Server();
			s.start(port, threads, backlog);
		}

		else {
			System.out.println("usage: java Server <port> [threads] [backlog-length]");
		}
	}
}
